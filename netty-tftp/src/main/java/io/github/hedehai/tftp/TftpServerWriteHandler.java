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
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static io.github.hedehai.tftp.packet.enums.TftpError.*;
import static io.github.hedehai.tftp.util.TftpConstants.*;

/**
 * 基本流程为：接收WRQ报文，发送ACK报文，接收DATA报文，发送ACK报文。
 *
 * @author hedehai
 * @date 2020/8/9.
 */
public class TftpServerWriteHandler extends SimpleChannelInboundHandler<BaseTftpPacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TftpServerWriteHandler.class);

    private static final String MESSAGE_FORMAT_1 = "发送报文:{}";

    private RandomAccessFile raf;

    private int blockSize;

    private int timeout;

    private int blockNumber;

    private int retries;

    private TftpServer tftpServer;


    public TftpServerWriteHandler(TftpServer tftpServer) {
        this.tftpServer = tftpServer;
        timeout = DEFAULT_TIMEOUT;
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BaseTftpPacket tftpPacket) throws Exception {
        LOGGER.debug("收到报文{}", tftpPacket);
        //
        switch (tftpPacket.getOpcode()) {
            case WRQ:
                handleWriteRequestPacket(ctx, (TftpWriteRequestPacket) tftpPacket);
                break;
            case DATA:
                handleDataPacket(ctx, (TftpDataPacket) tftpPacket);
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
     * 写请求处理
     *
     * @param ctx
     * @param writePacket
     */
    private void handleWriteRequestPacket(ChannelHandlerContext ctx, TftpWriteRequestPacket writePacket) {
        // 写请求预处理
        File file = preHandleWriteRequest(ctx, writePacket);
        if (file == null) {
            return;
        }
        // 块大小选项
        blockSize = writePacket.getBlockSize() == null ? DEFAULT_BLOCK_SIZE : writePacket.getBlockSize();
        // 块大小不能超过MAX_BLOCK_SIZE，否则会被截断
        blockSize = Math.min(blockSize, MAX_BLOCK_SIZE);
        //
        if (writePacket.getTransferSize() != null) {
            long fileLength = writePacket.getTransferSize();
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
            LOGGER.debug(MESSAGE_FORMAT_1, optionAckPacket);

            ctx.writeAndFlush(optionAckPacket);
        } else {
            // 应答 0
            TftpAckPacket ackPacket = new TftpAckPacket(0);
            LOGGER.debug(MESSAGE_FORMAT_1, ackPacket);
            ctx.writeAndFlush(ackPacket);
        }
        // 下一个报文的编号为1
        blockNumber = 1;
    }


    /**
     * 写请求预处理
     *
     * @param ctx
     * @param writePacket
     * @return
     */
    private File preHandleWriteRequest(ChannelHandlerContext ctx, TftpWriteRequestPacket writePacket) {
        // 模式处理，仅支持octet模式
        String mode = writePacket.getMode();
        if (!Objects.equals(mode, TftpRequestPacket.MODE_OCTET)) {
            LOGGER.error("不支持此模式, mode:{}", mode);
            sendErrorPacket(ctx, MODE_NOT_SUPPORTED);
            return null;
        }
        // 若不允许写，则发送错误报文
        if (!tftpServer.allowWrite) {
            LOGGER.error("没有设置写权限");
            sendErrorPacket(ctx, NO_WRITE_PERMISSION);
            return null;
        }
        // 若不允许覆盖，则发送错误报文
        if (!tftpServer.allowOverwrite) {
            LOGGER.error("没有设置覆盖权限");
            sendErrorPacket(ctx, NO_OVERWRITE_PERMISSION);
            return null;
        }
        //
        File file = new File(tftpServer.rootDir, writePacket.getFilename());
        // 若文件不存在，则创建
        if (!file.exists()) {
            try {
                boolean created = file.createNewFile();
                if (created) {
                    LOGGER.debug("文件不存在，新建文件");
                }
            } catch (IOException exp) {
                LOGGER.error("创建文件失败", exp);
                sendErrorPacket(ctx, ACCESS_VIOLATION);
                return null;
            }
        }
        //
        try {
            raf = new RandomAccessFile(file, "rw");
        } catch (FileNotFoundException exp) {
            LOGGER.error("文件不存在", exp);
            sendErrorPacket(ctx, TftpError.FILE_NOT_FOUND);
            return null;
        }
        return file;
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
                        ThreadPoolUtils.getInstance().schedule((Callable<ChannelFuture>) ctx::close,
                                LINGER_TIME, TimeUnit.SECONDS);
                    }
                } catch (Exception exp) {
                    LOGGER.error("写入文件失败", exp);
                    sendErrorPacket(ctx, ACCESS_VIOLATION);
                    return;
                }
                TftpAckPacket ackPacket = new TftpAckPacket(dataPacket.getBlockNumber());
                LOGGER.debug("发送Ack报文：{}", ackPacket);
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
                LOGGER.error("写达到最大重试次数");
                sendErrorPacket(ctx, UNDEFINED);
                return;
            }
            LOGGER.warn("data不正常，{}秒后重传上一个ack包", timeout);
            // 服务端实际的超时等待时间要比客户端的小一些
            int delayTime = timeout - 1;
            ThreadPoolUtils.getInstance().schedule(() -> {
                TftpAckPacket ackPacket = new TftpAckPacket(dataPacket.getBlockNumber());
                LOGGER.debug("发送Ack报文：{}", ackPacket);
                ctx.writeAndFlush(ackPacket);
            }, delayTime, TimeUnit.SECONDS);
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


}

