package io.github.hedehai.tftp;


import io.github.hedehai.tftp.packet.*;
import io.github.hedehai.tftp.packet.enums.TftpError;
import io.github.hedehai.tftp.util.ThreadPoolUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static io.github.hedehai.tftp.packet.enums.TftpError.*;


/**
 * @author hedehai
 * @date 2020/8/9.
 */
public class TftpServerHandler extends SimpleChannelInboundHandler<BaseTftpPacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TftpServerHandler.class);

    private static final int DEFAULT_BLOCK_SIZE = 512;

    private static final int MAX_BLOCK_SIZE = 8192;

    private static final int DEFAULT_TIMEOUT = 3;

    private static final int MAX_BLOCK_NUMBER = 65536;

    private static final int LINGER_TIME = 3;

    private volatile RandomAccessFile raf;

    private volatile long fileLength;

    private volatile int blockSize;

    private volatile byte[] blockBuffer;

    private volatile int blockNumber;

    private volatile boolean readFinished = false;

    private volatile int retries;

    private volatile int timeout;

    private TftpServer tftpServer;


    public TftpServerHandler(TftpServer tftpServer) {
        this.tftpServer = tftpServer;
        timeout = DEFAULT_TIMEOUT;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("连接建立, remoteAdress = {}", ctx.channel().remoteAddress());
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BaseTftpPacket tftpPacket) throws Exception {
        LOGGER.debug("收到报文{}", tftpPacket);
        //
        switch (tftpPacket.getOpcode()) {
            case RRQ:
                handleReadRequestPacket(ctx, (TftpReadRequestPacket) tftpPacket);
                break;
            case WRQ:
                handleWriteRequestPacket(ctx, (TftpWriteRequestPacket) tftpPacket);
                break;
            case DATA:
                handleDataPacket(ctx, (TftpDataPacket) tftpPacket);
                break;
            case ACK:
                handleAckPacket(ctx, (TftpAckPacket) tftpPacket);
                break;
            case ERROR:
                handleErrorPacket(ctx, (TftpErrorPacket) tftpPacket);
                break;
            default:
                // nop 不会执行到这里，在channel中就被处理掉了
                break;
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("连接关闭, remoteAdress = {}\n", ctx.channel().remoteAddress());
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.error("未处理异常", cause);
    }


    /**
     * @param ctx
     * @param readPacket
     */
    private void handleReadRequestPacket(ChannelHandlerContext ctx, TftpReadRequestPacket readPacket) {
        // 模式处理，仅支持octet模式
        String mode = readPacket.getMode();
        if (!Objects.equals(mode, TftpRequestPacket.MODE_OCTET)) {
            LOGGER.error("不支持此模式, mode:{}", mode);
            sendErrorPacket(ctx, MODE_NOT_SUPPORTED);
            return;
        }
        // 若不允许读，则发送错误报文
        if (!tftpServer.allowRead) {
            LOGGER.error("没有设置读权限");
            sendErrorPacket(ctx, NO_READ_PERMISSION);
            return;
        }
        // 块大小选项
        blockSize = readPacket.getBlockSize() == null ? DEFAULT_BLOCK_SIZE : readPacket.getBlockSize();
        blockBuffer = new byte[blockSize];
        // 初始化文件读取器
        File file = new File(tftpServer.rootDir, readPacket.getFilename());
        fileLength = file.length();
        try {
            raf = new RandomAccessFile(file, "r");

        } catch (FileNotFoundException exp) {
            LOGGER.error("文件不存在", exp);
            sendErrorPacket(ctx, FILE_NOT_FOUND);
            return;
        }
        LOGGER.info("读请求, 文件：{} , 大小：{}B, 块大小：{}B, 分{}次传输.",
                file, fileLength, blockSize, (fileLength / blockSize) + 1);

        //  若带协商，则发送协商应答报文
        if (readPacket.isNegotiate()) {
            // 传输大小
            Long transferSize = readPacket.getTransferSize() != null ? fileLength : null;
            // 超时时间
            timeout = readPacket.getTimeout() != null ? readPacket.getTimeout() : DEFAULT_TIMEOUT;
            // 发送 OACK 报文
            TftpOptionAckPacket optionAckPacket = new TftpOptionAckPacket(readPacket.getBlockSize(),
                    readPacket.getTimeout(), transferSize);
            LOGGER.debug("发送报文：" + optionAckPacket);
            ctx.writeAndFlush(optionAckPacket);
            //
            blockNumber = 0;
        } else {
            // 传输第1块
            ThreadPoolUtils.getInstance().execute(() -> {
                blockNumber = 1;
                try {
                    TftpDataPacket dataPacket = createDataPacket(1);
                    if (dataPacket != null) {
                        LOGGER.debug("发送报文：" + dataPacket);
                        ctx.writeAndFlush(dataPacket);
                    }
                } catch (IOException exp) {
                    LOGGER.error("读取文件失败", exp);
                    sendErrorPacket(ctx, ACCESS_VIOLATION);
                }
            });
        }
    }


    /**
     * @param ctx
     * @param writePacket
     */
    private void handleWriteRequestPacket(ChannelHandlerContext ctx, TftpWriteRequestPacket writePacket) {
        // 模式处理，仅支持octet模式
        String mode = writePacket.getMode();
        if (!Objects.equals(mode, TftpRequestPacket.MODE_OCTET)) {
            LOGGER.error("不支持此模式, mode:{}", mode);
            sendErrorPacket(ctx, UNDEFINED);
            return;
        }
        // 若不允许写，则发送错误报文
        if (!tftpServer.allowWrite) {
            LOGGER.error("没有设置写权限");
            sendErrorPacket(ctx, NO_READ_PERMISSION);
            return;
        }
        //
        File file = new File(tftpServer.rootDir, writePacket.getFilename());
        if (file.exists()) {
            // 若不允许覆盖，则发送错误报文
            if (!tftpServer.allowOverwrite) {
                LOGGER.error("没有设置覆盖权限");
                sendErrorPacket(ctx, NO_OVERWRITE_PERMISSION);
                return;
            }
        } else {
            try {
                file.createNewFile();
            } catch (IOException exp) {
                LOGGER.error("创建文件失败", exp);
                sendErrorPacket(ctx, ACCESS_VIOLATION);
                return;
            }
        }
        //
        try {
            raf = new RandomAccessFile(file, "rw");
        } catch (FileNotFoundException exp) {
            LOGGER.error("文件不存在", exp);
            sendErrorPacket(ctx, TftpError.FILE_NOT_FOUND);
            return;
        }
        // 块大小选项
        blockSize = writePacket.getBlockSize() == null ? DEFAULT_BLOCK_SIZE : writePacket.getBlockSize();
        // 块大小不能超过MAX_BLOCK_SIZE，否则会被截断
        blockSize = Math.min(blockSize, MAX_BLOCK_SIZE);
        blockBuffer = new byte[blockSize];
        //
        if (writePacket.getTransferSize() != null) {
            fileLength = writePacket.getTransferSize();
            LOGGER.info("写请求, 文件：{} , 大小：{}B, 块大小：{}B, 分{}次传输.",
                    file, fileLength, blockSize, (fileLength / blockSize) + 1);
        } else {
            LOGGER.info("写请求, 文件：{} , 块大小：{}B", file, blockSize);
        }

        // 若带协商，则发送协商应答报文
        if (writePacket.isNegotiate()) {
            // 剩余空间不足，则发送错误报文
            if (writePacket.getTransferSize() != null &&
                    file.getFreeSpace() < writePacket.getTransferSize()) {
                sendErrorPacket(ctx, TftpError.OUT_OF_SPACE);
                return;
            }
            TftpOptionAckPacket optionAckPacket = new TftpOptionAckPacket(writePacket.getBlockSize(),
                    writePacket.getTimeout(), writePacket.getTransferSize());
            LOGGER.debug("发送报文：" + optionAckPacket);

            ctx.writeAndFlush(optionAckPacket);
        } else {
            // 应答 0
            TftpAckPacket ackPacket = new TftpAckPacket(0);
            LOGGER.debug("发送Ack报文：" + ackPacket);
            ctx.writeAndFlush(ackPacket);
        }
        // 下一个报文的编号为1
        blockNumber = 1;
    }


    /**
     * @param ctx
     * @param ackPacket
     */
    private void handleAckPacket(ChannelHandlerContext ctx, TftpAckPacket ackPacket) {
        // 当ack的blockNumber和上一个blockNumber一样时，则认为应答正常。
        if (ackPacket.getBlockNumber() == blockNumber) {
            // 若读取完毕，则
            if (readFinished) {
                LOGGER.info("读取完毕");
                // 延迟关闭连接
                ctx.channel().eventLoop().schedule(() -> {
                    ctx.close();
                }, LINGER_TIME, TimeUnit.SECONDS);
                return;
            }
            //
            ThreadPoolUtils.getInstance().execute(() -> {
                // 块号加1
                blockNumber++;
                if (blockNumber == MAX_BLOCK_NUMBER) {
                    // 变成1，还是变成0？ 应当是从0开始，这个从windows的tftp客户端可以看出来
                    blockNumber = 0;
                    LOGGER.info("blockNumber重新开始");
                }
                try {
                    //
                    TftpDataPacket dataPacket = createDataPacket(blockNumber);
                    if (dataPacket != null) {
                        LOGGER.debug("发送报文：" + dataPacket);
                        ctx.writeAndFlush(dataPacket);
                    }
                } catch (Exception exp) {
                    LOGGER.error("写入文件失败", exp);
                    sendErrorPacket(ctx, ACCESS_VIOLATION);
                }
                retries = 0;
            });
        }
        // 如果应答不正常，就重传上一个包。
        else {
            retries++;
            // 达到最大重试次数时退出
            if (retries > tftpServer.maxRetries) {
                LOGGER.error("达到最大重试次数");
                sendErrorPacket(ctx, UNDEFINED);
                return;
            }
            LOGGER.warn("ack包不正常，{}秒后重传上一个data包", timeout);
            // 服务端实际的超时等待时间要比客户端的小一些
            int delayTime = timeout - 1;
            ctx.channel().eventLoop().schedule(() -> {
                TftpDataPacket dataPacket = new TftpDataPacket(blockNumber, blockBuffer);
                LOGGER.debug("发送报文：" + dataPacket);
                ctx.writeAndFlush(dataPacket);
            }, delayTime, TimeUnit.SECONDS);
        }
    }


    /**
     * @param ctx
     * @param dataPacket
     */
    private void handleDataPacket(ChannelHandlerContext ctx, TftpDataPacket dataPacket) {
        // 当data的blockNumber和blockNumber一样时，则认为正常。
        if (dataPacket.getBlockNumber() == blockNumber) {
            ThreadPoolUtils.getInstance().execute(() -> {
                try {
                    // 读取包数据，写入文件
                    byte[] bytes = dataPacket.getBlockData();
                    raf.write(bytes);
                    if (bytes.length < blockSize) {
                        raf.close();
                        LOGGER.info("写入完毕");
                        // 延迟关闭连接
                        ctx.channel().eventLoop().schedule(() -> {
                            ctx.close();
                        }, LINGER_TIME, TimeUnit.SECONDS);
                    }
                } catch (Exception exp) {
                    LOGGER.error("写入文件失败", exp);
                    sendErrorPacket(ctx, ACCESS_VIOLATION);
                    return;
                }
                TftpAckPacket ackPacket = new TftpAckPacket(dataPacket.getBlockNumber());
                LOGGER.debug("发送Ack报文：" + ackPacket);
                ctx.writeAndFlush(ackPacket);
                // 块号加1
                blockNumber++;
                if (blockNumber == MAX_BLOCK_NUMBER) {
                    // 变成1，还是变成0？ 应当是从0开始，这个从windows的tftp客户端可以看出来
                    blockNumber = 0;
                    LOGGER.info("blockNumber重新开始");
                }
            });
        }
        // 如果不正常，就重传上一个包。
        else {
            retries++;
            // 达到最大重试次数时退出
            if (retries > tftpServer.maxRetries) {
                LOGGER.error("达到最大重试次数");
                sendErrorPacket(ctx, UNDEFINED);
                return;
            }
            LOGGER.warn("data不正常，{}秒后重传上一个ack包", timeout);
            // 服务端实际的超时等待时间要比客户端的小一些
            int delayTime = timeout - 1;
            ThreadPoolUtils.getInstance().schedule(() -> {
                TftpAckPacket ackPacket = new TftpAckPacket(dataPacket.getBlockNumber());
                LOGGER.debug("发送Ack报文：" + ackPacket);
                ctx.writeAndFlush(ackPacket);
            }, delayTime, TimeUnit.SECONDS);
        }


    }


    /**
     * @param ctx
     * @param errorPacket
     */
    private void handleErrorPacket(ChannelHandlerContext ctx, TftpErrorPacket errorPacket) {
        // 收到错误报文之后，要断开连接
        ctx.close();
    }


    /**
     * @param errorType
     * @param ctx
     */
    private void sendErrorPacket(ChannelHandlerContext ctx, TftpError errorType) {
        TftpErrorPacket errorPacket = new TftpErrorPacket(errorType);
        LOGGER.error("发送错误报文：" + errorPacket);
        ctx.writeAndFlush(errorPacket);
        //
        ctx.close();
    }


    /**
     * 注意：当文件的大小刚好为blockSize的整数倍时，最后还需要发送一个内容为空的数据包
     *
     * @return
     * @throws Exception
     */
    private TftpDataPacket createDataPacket(int blockNumber) throws IOException {
        TftpDataPacket packet;
        int readCount = raf.read(blockBuffer);
        // 当读不到内容时
        if (readCount == -1) {
            raf.close();
            readFinished = true;
            // 文件读取完毕后，还需检查文件大小是否等于blockSize的整数倍，
            // 若是，则需要再补发一个空包
            if (fileLength % blockSize == 0) {
                // 内容为空的数据包
                packet = new TftpDataPacket(blockNumber, new byte[]{});
            } else {
                return null;
            }
        } else {
            // 当 readCount小于blockSize时，说明它是最后一个数据块
            if (readCount < blockSize) {
                raf.close();
                readFinished = true;
                //
                byte[] lastBlockData = Arrays.copyOf(blockBuffer, readCount);
                packet = new TftpDataPacket(blockNumber, lastBlockData);
            } else {
                packet = new TftpDataPacket(blockNumber, blockBuffer);
            }
        }
        return packet;
    }





}

