package io.github.hedehai.tftp;


import io.github.hedehai.tftp.packet.*;
import io.github.hedehai.tftp.packet.enums.TftpError;
import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.github.hedehai.tftp.util.ThreadPoolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static io.github.hedehai.tftp.packet.enums.TftpError.*;
import static io.github.hedehai.tftp.packet.enums.TftpOpcode.RRQ;
import static io.github.hedehai.tftp.packet.enums.TftpOpcode.WRQ;


/**
 * @author hedehai
 * @date 2020/8/9.
 */
public class TftpServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TftpServerHandler.class);

    private static final int DEFAULT_BLOCK_SIZE = 512;

    private static final int MAX_BLOCK_SIZE = 8192;


    private static final int DEFAULT_TIMEOUT = 3;


    private static final Map<String, TftpConnection> CONNECTION_MAP = new HashMap<>();


    private TftpServer tftpServer;


    public TftpServerHandler(TftpServer tftpServer) {
        this.tftpServer = tftpServer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket udpPacket) throws Exception {
        ByteBuf buf = udpPacket.content();
        // 获取操作码，注意读针要回到最初的位置
        TftpOpcode opcode = TftpOpcode.get(buf.readUnsignedShort());
        buf.readerIndex(buf.readerIndex() - 2);

        // 连接相关的操作。在处理报文前，要先确保连接对象存在。
        String connectionId = udpPacket.sender().toString() + "->" + udpPacket.recipient().toString();
        if (CONNECTION_MAP.get(connectionId) == null) {
            // 如果是读请求或写请求，则新建连接
            if (opcode == RRQ || opcode == WRQ) {
                CONNECTION_MAP.put(connectionId, new TftpConnection(connectionId));
                LOGGER.info("新建连接, id:{}", connectionId);
            }
            // 其它报文为非法
            else {
                LOGGER.warn("报文非法，因为是未建立连接, opcode:{}", opcode);
                sendErrorPacket(UNKNOWN_TID, ctx, udpPacket);
                return;
            }
        }

        TftpConnection connection = CONNECTION_MAP.get(connectionId);
        //
        switch (opcode) {
            case RRQ:
                handleReadRequestPacket(connection, ctx, udpPacket);
                break;
            case WRQ:
                handleWriteRequestPacket(connection, ctx, udpPacket);
                break;
            case DATA:
                handleDataPacket(connection, ctx, udpPacket);
                break;
            case ACK:
                handleAckPacket(connection, ctx, udpPacket);
                break;
            case ERROR:
                handleErrorPacket(connection, ctx, udpPacket);
                break;
            default:
                LOGGER.error("无法处理的报文类型：" + opcode);
                sendErrorPacket(ILLEGAL_OPERATION, ctx, udpPacket);
                break;
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.error("未处理异常", cause);
    }


    /**
     * @param ctx
     * @param udpPacket
     */
    private void handleReadRequestPacket(TftpConnection connection,
                                         ChannelHandlerContext ctx, DatagramPacket udpPacket) {
        TftpReadRequestPacket packet1 = new TftpReadRequestPacket(udpPacket.content());
        LOGGER.info("收到报文：" + packet1);
        // 模式处理，仅支持octet模式
        String mode = packet1.getMode();
        if (!Objects.equals(mode, TftpRequestPacket.MODE_OCTET)) {
            LOGGER.error("不支持此模式, mode:{}", mode);
            sendErrorPacket(MODE_NOT_SUPPORTED, ctx, udpPacket);
            return;
        }
        // 若不允许读，则发送错误报文
        if (!tftpServer.allowRead) {
            LOGGER.error("没有设置读权限");
            sendErrorPacket(NO_READ_PERMISSION, ctx, udpPacket);
            return;
        }
        // 块大小选项
        int blockSize = packet1.getBlockSize() == null ? DEFAULT_BLOCK_SIZE : packet1.getBlockSize();
        connection.setBlockSize(blockSize);
        connection.setReadBuffer(new byte[blockSize]);
        // 初始化文件读取器
        File file = new File(tftpServer.rootDir, packet1.getFilename());
        long fileLength = file.length();
        try {
            connection.setRaf(new RandomAccessFile(file, "r"));
            connection.setFileLength(fileLength);
        } catch (FileNotFoundException exp) {
            LOGGER.error("文件不存在", exp);
            sendErrorPacket(FILE_NOT_FOUND, ctx, udpPacket);
            return;
        }
        LOGGER.info("read request, 文件：{} , 大小：{}B, 块大小：{}B, 分{}次传输.",
                file, fileLength, blockSize, (fileLength / blockSize) + 1);

        //  若为协商，则发送协商报文
        if (packet1.isNegotiate()) {
            // 传输大小
            Long transferSize = packet1.getTransferSize() != null ? fileLength : null;
            // 超时时间
            int timeout = packet1.getTimeout() != null ? packet1.getTimeout() : DEFAULT_TIMEOUT;
            connection.setTimeout(timeout);
            // 发送 OACK 报文
            TftpOptionAckPacket optionAckPacket = new TftpOptionAckPacket(packet1.getBlockSize(),
                    packet1.getTimeout(), transferSize);
            LOGGER.debug("发送报文：" + optionAckPacket);
            DatagramPacket responsePacket = new DatagramPacket(optionAckPacket.toByteBuf(), udpPacket.sender());
            ctx.writeAndFlush(responsePacket);
            //
            connection.setBlockNumber(0);
        } else {
            // 传输第1块
            ThreadPoolUtils.getInstance().execute(() -> {
                connection.setBlockNumber(1);
                try {
                    TftpDataPacket dataPacket = connection.createDataPacket(1);
                    if (dataPacket != null) {
                        LOGGER.debug("发送报文：" + dataPacket);
                        DatagramPacket responsePacket = new DatagramPacket(dataPacket.toByteBuf(), udpPacket.sender());
                        ctx.writeAndFlush(responsePacket);
                    }
                } catch (IOException exp) {
                    LOGGER.error("读取文件失败", exp);
                    sendErrorPacket(ACCESS_VIOLATION, ctx, udpPacket);
                }
            });
        }
    }


    /**
     * @param ctx
     * @param udpPacket
     */
    private void handleWriteRequestPacket(TftpConnection connection,
                                          ChannelHandlerContext ctx, DatagramPacket udpPacket) {
        TftpWriteRequestPacket packet1 = new TftpWriteRequestPacket(udpPacket.content());
        LOGGER.info("收到报文：" + packet1);
        // 模式处理，仅支持octet模式
        String mode = packet1.getMode();
        if (!Objects.equals(mode, TftpRequestPacket.MODE_OCTET)) {
            LOGGER.error("不支持此模式, mode:{}", mode);
            sendErrorPacket(UNDEFINED, ctx, udpPacket);
            return;
        }
        // 若不允许写，则发送错误报文
        if (!tftpServer.allowWrite) {
            LOGGER.error("没有设置写权限");
            sendErrorPacket(NO_READ_PERMISSION, ctx, udpPacket);
            return;
        }
        //
        File file = new File(tftpServer.rootDir, packet1.getFilename());
        if (file.exists()) {
            // 若不允许覆盖，则发送错误报文
            if (!tftpServer.allowOverwrite) {
                LOGGER.error("没有设置覆盖权限");
                sendErrorPacket(NO_OVERWRITE_PERMISSION, ctx, udpPacket);
                return;
            }
        } else {
            try {
                file.createNewFile();
            } catch (IOException exp) {
                LOGGER.error("创建文件失败", exp);
                sendErrorPacket(ACCESS_VIOLATION, ctx, udpPacket);
                return;
            }
        }
        //
        try {
            connection.setRaf(new RandomAccessFile(file, "rw"));
        } catch (FileNotFoundException exp) {
            LOGGER.error("文件不存在", exp);
            sendErrorPacket(TftpError.FILE_NOT_FOUND, ctx, udpPacket);
            return;
        }

        // 块大小选项
        int blockSize = packet1.getBlockSize() == null ? DEFAULT_BLOCK_SIZE : packet1.getBlockSize();
        // 块大小不能超过MAX_BLOCK_SIZE，否则会被截断
        blockSize = Math.min(blockSize, MAX_BLOCK_SIZE);
        connection.setBlockSize(blockSize);
        connection.setReadBuffer(new byte[blockSize]);

        // 若为协商，则发送协商报文
        if (packet1.isNegotiate()) {
            // 剩余空间不足，则发送错误报文
            if (packet1.getTransferSize() != null &&
                    file.getFreeSpace() < packet1.getTransferSize()) {
                sendErrorPacket(TftpError.OUT_OF_SPACE, ctx, udpPacket);
                return;
            }
            TftpOptionAckPacket optionAckPacket = new TftpOptionAckPacket(packet1.getBlockSize(),
                    packet1.getTimeout(), packet1.getTransferSize());
            LOGGER.debug("发送报文：" + optionAckPacket);
            DatagramPacket responsePacket = new DatagramPacket(optionAckPacket.toByteBuf(), udpPacket.sender());
            ctx.writeAndFlush(responsePacket);
        } else {
            // 应答 0
            TftpAckPacket ackPacket = new TftpAckPacket(0);
            LOGGER.debug("发送Ack报文：" + ackPacket);
            DatagramPacket responsePacket = new DatagramPacket(ackPacket.toByteBuf(), udpPacket.sender());
            ctx.writeAndFlush(responsePacket);
        }
        // 下一个报文的编号为1
        connection.setBlockNumber(1);
    }


    /**
     * @param ctx
     * @param udpPacket
     */
    private void handleAckPacket(TftpConnection connection,
                                 ChannelHandlerContext ctx, DatagramPacket udpPacket) {
        TftpAckPacket packet1 = new TftpAckPacket(udpPacket.content());
        LOGGER.debug("收到报文：" + packet1);
        // 当ack的blockNumber和上一个blockNumber一样时，则认为应答正常。
        if (packet1.getBlockNumber() == connection.getBlockNumber()) {
            if (connection.isReadFinished()) {
                LOGGER.info("读取完毕");
                removeConnection(connection.getId());
                return;
            }
            ThreadPoolUtils.getInstance().execute(() -> {
                // 块号加1
                connection.increaseBlockNumber();
                try {
                    //
                    TftpDataPacket dataPacket = connection.createDataPacket(connection.getBlockNumber());
                    if (dataPacket != null) {
                        LOGGER.debug("发送报文：" + dataPacket);
                        DatagramPacket responsePacket = new DatagramPacket(dataPacket.toByteBuf(), udpPacket.sender());
                        ctx.writeAndFlush(responsePacket);
                    }
                } catch (Exception exp) {
                    LOGGER.error("写入文件失败", exp);
                    sendErrorPacket(ACCESS_VIOLATION, ctx, udpPacket);
                }
                connection.resetReties();
            });
        }
        // 如果不正常，就重传上一个包。
        else {
            connection.increaseReties();
            // 达到最大重试次数时退出
            if (connection.getRetries() > tftpServer.maxRetries) {
                LOGGER.error("达到最大重试次数");
                sendErrorPacket(UNDEFINED, ctx, udpPacket);
                return;
            }
            LOGGER.warn("ack包不正常，{}秒后重传上一个data包", connection.getTimeout());
            // 服务端实际的超时等待时间要比客户端的小一些
            int waitTime = connection.getTimeout() - 1;
            ThreadPoolUtils.getInstance().schedule(() -> {
                TftpDataPacket dataPacket = new TftpDataPacket(connection.getBlockNumber(),
                        connection.getReadBuffer());
                LOGGER.debug("发送报文：" + dataPacket);
                DatagramPacket responsePacket = new DatagramPacket(dataPacket.toByteBuf(), udpPacket.sender());
                ctx.writeAndFlush(responsePacket);
            }, waitTime, TimeUnit.SECONDS);
        }
    }


    /**
     * @param ctx
     * @param udpPacket
     */
    private void handleDataPacket(TftpConnection connection,
                                  ChannelHandlerContext ctx, DatagramPacket udpPacket) {
        TftpDataPacket dataPacket = new TftpDataPacket(udpPacket.content());
        LOGGER.debug("收到报文：" + dataPacket);

        // 当data的blockNumber和blockNumber一样时，则认为正常。
        if (dataPacket.getBlockNumber() == connection.getBlockNumber()) {
            ThreadPoolUtils.getInstance().execute(() -> {
                try {
                    // 读取包数据，写入文件
                    byte[] bytes = dataPacket.getBlockData();
                    connection.write(bytes);
                    if (bytes.length < connection.getBlockSize()) {
                        connection.closeFile();
                        LOGGER.info("写入完毕");
                        removeConnection(connection.getId());
                    }
                } catch (Exception exp) {
                    LOGGER.error("写入文件失败", exp);
                    sendErrorPacket(ACCESS_VIOLATION, ctx, udpPacket);
                    return;
                }

                // 发送应答报文
                sendAckPacket(dataPacket, ctx, udpPacket);

                // 块号加1
                connection.increaseBlockNumber();
            });

        }
        // 如果不正常，就重传上一个包。
        else {
            connection.increaseReties();
            // 达到最大重试次数时退出
            if (connection.getRetries() > tftpServer.maxRetries) {
                LOGGER.error("达到最大重试次数");
                sendErrorPacket(UNDEFINED, ctx, udpPacket);
                return;
            }
            LOGGER.warn("data不正常，{}秒后重传上一个ack包", connection.getTimeout());
            // 服务端实际的超时等待时间要比客户端的小一些
            int waitTime = connection.getTimeout() - 1;
            ThreadPoolUtils.getInstance().schedule(() -> {
                sendAckPacket(dataPacket, ctx, udpPacket);
            }, waitTime, TimeUnit.SECONDS);
        }


    }

    /**
     * @param ctx
     * @param udpPacket
     */
    private void handleErrorPacket(TftpConnection connection,
                                   ChannelHandlerContext ctx, DatagramPacket udpPacket) {
        TftpErrorPacket packet1 = new TftpErrorPacket(udpPacket.content());
        LOGGER.error("收到错误报文，报文：{}", packet1);
        removeConnection(connection.getId());

    }

    /**
     * 根据收到的Data发送Ack
     *
     * @param dataPacket
     * @param ctx
     * @param udpPacket
     */
    private void sendAckPacket(TftpDataPacket dataPacket,
                               ChannelHandlerContext ctx, DatagramPacket udpPacket) {
        TftpAckPacket ackPacket = new TftpAckPacket(dataPacket.getBlockNumber());
        LOGGER.debug("发送Ack报文：" + ackPacket);
        DatagramPacket responsePacket = new DatagramPacket(ackPacket.toByteBuf(), udpPacket.sender());
        ctx.writeAndFlush(responsePacket);
    }


    /**
     * 发送错误报文
     *
     * @param tftpError
     * @param ctx
     * @param udpPacket
     */
    private void sendErrorPacket(TftpError tftpError, ChannelHandlerContext ctx,
                                 DatagramPacket udpPacket) {
        TftpErrorPacket errorPacket = new TftpErrorPacket(tftpError);
        LOGGER.debug("发送错误报文：" + errorPacket);
        DatagramPacket responsePacket = new DatagramPacket(errorPacket.toByteBuf(), udpPacket.sender());
        ctx.writeAndFlush(responsePacket);
        //移除连接
        String connectionId = udpPacket.sender().toString() + "->" + udpPacket.recipient().toString();
        removeConnection(connectionId);
    }


    /**
     * @param connectionId
     */
    private void removeConnection(String connectionId) {
        //延迟n秒
        int delayRemoveConnection = 3;
        if (CONNECTION_MAP.get(connectionId) != null) {
            LOGGER.info("{}秒后移除连接, id:{}\n", delayRemoveConnection, connectionId);
            ThreadPoolUtils.getInstance().schedule(() -> {
                CONNECTION_MAP.remove(connectionId);
            }, delayRemoveConnection, TimeUnit.SECONDS);
        }
    }


}