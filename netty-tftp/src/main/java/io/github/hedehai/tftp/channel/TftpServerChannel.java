package io.github.hedehai.tftp.channel;

import io.github.hedehai.tftp.packet.BaseTftpPacket;
import io.github.hedehai.tftp.packet.TftpErrorPacket;
import io.github.hedehai.tftp.packet.enums.TftpError;
import io.github.hedehai.tftp.util.TftpPacketUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.AbstractNioMessageChannel;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.hedehai.tftp.packet.enums.TftpOpcode.RRQ;
import static io.github.hedehai.tftp.packet.enums.TftpOpcode.WRQ;

/**
 * @author hedehai
 * @date 2020/8/15.
 */
public class TftpServerChannel extends AbstractNioMessageChannel implements ServerChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(TftpServerChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();


    /**
     * 创建 nio channel
     *
     * @return
     */
    private static DatagramChannel newDatagramChannel() {
        try {
            return DEFAULT_SELECTOR_PROVIDER.openDatagramChannel();
        } catch (Exception e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }


    private Map<SocketAddress, TftpServerChildChannel> childChannelMap
            = new HashMap<>();


    private DefaultChannelConfig config;


    /**
     * 默认构造函数，会被bootstrap的channel调用
     */
    public TftpServerChannel() {
        super(null, TftpServerChannel.newDatagramChannel(), SelectionKey.OP_READ);
        config = new DefaultChannelConfig(this);
    }


    /**
     * 元数据
     *
     * @return
     */
    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }


    /**
     * 配置
     *
     * @return
     */
    @Override
    public DefaultChannelConfig config() {
        return config;
    }


    /**
     * 获取nio channel
     *
     * @return
     */
    @Override
    protected DatagramChannel javaChannel() {
        return (DatagramChannel) super.javaChannel();
    }

    /**
     * 是否活动
     *
     * @return
     */
    @Override
    public boolean isActive() {
        DatagramChannel nioChannel = javaChannel();
        return nioChannel.isOpen() && nioChannel.socket().isBound();
    }

    /**
     * 获取本地地址
     *
     * @return
     */
    @Override
    protected InetSocketAddress localAddress0() {
        return (InetSocketAddress) javaChannel().socket().getLocalSocketAddress();
    }

    /**
     * 获取远程地址
     *
     * @return
     */
    @Override
    protected InetSocketAddress remoteAddress0() {
        return (InetSocketAddress) javaChannel().socket().getRemoteSocketAddress();
    }


    /**
     * 绑定端口
     *
     * @param localAddress
     * @throws Exception
     */
    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        SocketUtils.bind(javaChannel(), localAddress);
    }


    /**
     * 连接至服务端。
     * 此为服务端，不需要此功能。
     *
     * @param remoteAddress
     * @param localAddress
     * @return
     * @throws Exception
     */
    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    /**
     * 完成连接至服务端。此为服务端，不需要此功能。
     *
     * @throws Exception
     */
    @Override
    protected void doFinishConnect() {
        throw new UnsupportedOperationException();
    }

    /**
     * 断开与服务器的连接。此为服务端，不需要此功能。
     *
     * @throws Exception
     */
    @Override
    protected void doDisconnect() {
        throw new UnsupportedOperationException();
    }


    /**
     * 获取或者创建子channel
     *
     * @param
     * @return
     */
    private TftpServerChildChannel getOrCreateChildChannel(BaseTftpPacket packet) throws IOException {
        InetSocketAddress remoteAddress = packet.getRemoteAddress();
        TftpServerChildChannel childChannel = childChannelMap.get(remoteAddress);
        if (childChannel == null) {
            LOGGER.debug("收到创建子channel请求， remoteAddress={}", remoteAddress);
            // 仅当 WRQ和RRQ时，才新建channel
            if (packet.getOpcode() == RRQ || packet.getOpcode() == WRQ) {
                childChannel = new TftpServerChildChannel(this, remoteAddress);
                // 激活子channel
                ChannelPipeline pipeline = pipeline();
                pipeline.fireChannelRead(childChannel);
                pipeline.fireChannelReadComplete();
                // 加到map中
                childChannelMap.put(remoteAddress, childChannel);
            }
            // 其它报文为非法
            else {
                sendError(TftpError.UNKNOWN_TID, remoteAddress);
                throw new IOException("报文非法，建立channel的报文应为WRQ和RRQ，此报文为:" + packet.getOpcode());
            }
        }
        return childChannel;
    }


    /**
     * 移除子channel
     */
    protected void removeChildChannel(InetSocketAddress remoteAddress) {
        childChannelMap.remove(remoteAddress);
    }


    @Override
    protected boolean doWriteMessage(Object o, ChannelOutboundBuffer channelOutboundBuffer) throws Exception {
        return false;
    }


    @Override
    protected int doReadMessages(List<Object> packetList) throws Exception {
        DatagramChannel nioChannel = javaChannel();
        DefaultChannelConfig config = config();
        RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
        ByteBuf data = allocHandle.allocate(config.getAllocator());
        allocHandle.attemptedBytesRead(data.writableBytes());
        try {
            ByteBuffer nioData = data.internalNioBuffer(data.writerIndex(), data.writableBytes());
            int pos = nioData.position();
            InetSocketAddress remoteAddress = (InetSocketAddress) nioChannel.receive(nioData);
            if (remoteAddress == null) {
                return 0;
            }
            // // 标示读了多少个字节
            allocHandle.lastBytesRead(nioData.position() - pos);
            // 写针要往前移
            data.writerIndex(data.writerIndex() + allocHandle.lastBytesRead());

            BaseTftpPacket tftpPacket;
            try {
                tftpPacket = TftpPacketUtils.create(data);
            } catch (Exception exp) {
                sendError(TftpError.ILLEGAL_OPERATION, remoteAddress);
                LOGGER.error("未识别的操作码", exp);
                return 0;
            }
            //
            tftpPacket.setRemoteAddress(remoteAddress);
            packetList.add(tftpPacket);
            // 由于加入了一个对象，所以返回1
            return 1;
        } catch (Throwable cause) {
            PlatformDependent.throwException(cause);
            return 0;
        } finally {
            data.release();
        }
    }


    /**
     * 获取底层Unsafe实例
     *
     * @return
     */
    @Override
    protected TftpServerChannelUnsafe newUnsafe() {
        return new TftpServerChannelUnsafe();
    }


    /**
     * 此为底层 eventloop注册，selector循环的地方。
     * 当网卡接收到数据时，会触发read()方法
     */
    private final class TftpServerChannelUnsafe extends AbstractNioUnsafe {

        private final List<Object> readBufList = new ArrayList<Object>();

        @Override
        public void read() {
            assert eventLoop().inEventLoop();
            final ChannelConfig config = config();
            final ChannelPipeline pipeline = pipeline();
            final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
            allocHandle.reset(config);

            boolean closed = false;
            Throwable exception = null;
            try {
                try {
                    do {
                        int localRead = doReadMessages(readBufList);
                        if (localRead == 0) {
                            break;
                        }
                        if (localRead < 0) {
                            closed = true;
                            break;
                        }

                        allocHandle.incMessagesRead(localRead);
                    } while (allocHandle.continueReading());
                } catch (Throwable t) {
                    exception = t;
                }

                for (Object aReadBufList : readBufList) {
                    BaseTftpPacket tftpPacket = (BaseTftpPacket) aReadBufList;
                    // 创建新的子channel或者获取现有的子channel
                    TftpServerChildChannel childChannel = getOrCreateChildChannel(tftpPacket);
                    // 将数据传送至pipeline
                    ChannelPipeline childPipeline = childChannel.pipeline();
                    childPipeline.fireChannelRead(tftpPacket);
                    childPipeline.fireChannelReadComplete();
                }
                //
                readBufList.clear();
                allocHandle.readComplete();

                if (exception != null) {
                    closed = closeOnReadError(exception);
                    pipeline.fireExceptionCaught(exception);
                }
                if (closed) {
                    if (isOpen()) {
                        close(voidPromise());
                    }
                }
            } catch (Exception exp) {
                LOGGER.error("unsafe read error", exp);
            }
        }

    }


    /**
     * @param error
     * @param remoteAddress
     */
    private void sendError(TftpError error, InetSocketAddress remoteAddress) {
        try {
            TftpErrorPacket errorPacket = new TftpErrorPacket(TftpError.UNKNOWN_TID);
            ByteBuf byteBuf = errorPacket.toByteBuf();
            ByteBuffer nioData = byteBuf.internalNioBuffer(byteBuf.readerIndex(), byteBuf.readableBytes());
            javaChannel().send(nioData, remoteAddress);
        } catch (Exception exp) {
            LOGGER.error("发送错误报文失败", exp);
        }

    }
}

