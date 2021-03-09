package io.github.hedehai.tftp;


import io.github.hedehai.tftp.packet.*;
import io.github.hedehai.tftp.packet.enums.TftpError;
import io.github.hedehai.tftp.util.ThreadPoolUtils;
import io.netty.channel.ChannelFuture;
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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static io.github.hedehai.tftp.packet.enums.TftpError.*;
import static io.github.hedehai.tftp.util.TftpConstants.*;


/**
 * 基本流程为：接收RRQ报文，发送DATA报文，接收ACK报文，发送DATA报文。
 *
 * @author hedehai
 * @date 2020/8/9.
 */
public class TftpServerReadHandler extends SimpleChannelInboundHandler<BaseTftpPacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TftpServerReadHandler.class);

    private static final String MESSAGE_FORMAT_1 = "发送报文:{}";

    private RandomAccessFile raf;

    private long fileLength;

    private int blockSize;

    private byte[] blockBuffer;

    private int blockNumber;

    private boolean readFinished = false;

    private int retries;

    private int timeout;

    private TftpServer tftpServer;


    public TftpServerReadHandler(TftpServer tftpServer) {
        this.tftpServer = tftpServer;
        timeout = DEFAULT_TIMEOUT;
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BaseTftpPacket tftpPacket) throws Exception {
        LOGGER.debug("收到报文{}", tftpPacket);
        switch (tftpPacket.getOpcode()) {
            case RRQ:
                handleReadRequestPacket(ctx, (TftpReadRequestPacket) tftpPacket);
                break;
            case ACK:
                handleAckPacket(ctx, (TftpAckPacket) tftpPacket);
                break;
            case ERROR:
                handleErrorPacket(ctx);
                break;
            default:
                // nop 不会执行到这里
                break;
        }
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
        // 读请求预处理
        File file = preHandleReadRequest(ctx, readPacket);
        if (file == null) {
            return;
        }
        // 块大小选项
        blockSize = readPacket.getBlockSize() == null ? DEFAULT_BLOCK_SIZE : readPacket.getBlockSize();
        blockBuffer = new byte[blockSize];
        fileLength = file.length();
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
            LOGGER.debug(MESSAGE_FORMAT_1, optionAckPacket);
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
                        LOGGER.debug(MESSAGE_FORMAT_1, dataPacket);
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
     * 读请求预处理
     *
     * @param ctx
     * @param readPacket
     * @return
     */
    private File preHandleReadRequest(ChannelHandlerContext ctx, TftpReadRequestPacket readPacket) {
        // 模式处理，仅支持octet模式
        String mode = readPacket.getMode();
        if (!Objects.equals(mode, TftpRequestPacket.MODE_OCTET)) {
            LOGGER.error("不支持此模式, mode:{}", mode);
            sendErrorPacket(ctx, MODE_NOT_SUPPORTED);
            return null;
        }
        // 若不允许读，则发送错误报文
        if (!tftpServer.allowRead) {
            LOGGER.error("没有设置读权限");
            sendErrorPacket(ctx, NO_READ_PERMISSION);
            return null;
        }

        // 初始化文件读取器
        File file = new File(tftpServer.rootDir, readPacket.getFilename());
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException exp) {
            LOGGER.error("文件不存在", exp);
            sendErrorPacket(ctx, FILE_NOT_FOUND);
            return null;
        }
        return file;
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
                ThreadPoolUtils.getInstance().schedule(
                        (Callable<ChannelFuture>) ctx::close, LINGER_TIME, TimeUnit.SECONDS);
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
                        LOGGER.debug("发送报文：{}", dataPacket);
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
                LOGGER.error("读达到最大重试次数");
                sendErrorPacket(ctx, UNDEFINED);
                return;
            }
            LOGGER.warn("ack包不正常，{}秒后重传上一个data包", timeout);
            // 服务端实际的超时等待时间要比客户端的小一些
            int delay = timeout - 1;
            ThreadPoolUtils.getInstance().schedule(() -> {
                TftpDataPacket dataPacket = new TftpDataPacket(blockNumber, blockBuffer);
                LOGGER.debug("发送报文：{}", dataPacket);
                ctx.writeAndFlush(dataPacket);
            }, delay, TimeUnit.SECONDS);
        }
    }


    /**
     * @param ctx
     */
    private void handleErrorPacket(ChannelHandlerContext ctx) {
        // 收到错误报文之后，断开连接
        ctx.close();
    }


    /**
     * @param errorType
     * @param ctx
     */
    private void sendErrorPacket(ChannelHandlerContext ctx, TftpError errorType) {
        TftpErrorPacket errorPacket = new TftpErrorPacket(errorType);
        LOGGER.warn("发送错误报文：{}", errorPacket);
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

