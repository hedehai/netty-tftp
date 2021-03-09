package io.github.hedehai.tftp.channel;

import io.github.hedehai.tftp.packet.BaseTftpPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoop;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * @author hedehai
 * @date 2020/8/15.
 */
public class TftpServerChildChannel extends AbstractChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private DefaultChannelConfig config;

    private InetSocketAddress remoteAddress;

    private boolean active;


    /**
     * @param parent
     * @param remoteAddress
     */
    public TftpServerChildChannel(Channel parent, InetSocketAddress remoteAddress) {
        super(parent);
        this.remoteAddress = remoteAddress;
        this.config = new DefaultChannelConfig(this);
        active = true;
    }


    /**
     * @return
     */
    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    /**
     * @return
     */
    @Override
    public DefaultChannelConfig config() {
        return config;
    }

    /**
     * 获取父channel
     *
     * @return
     */
    @Override
    public TftpServerChannel parent() {
        return (TftpServerChannel) super.parent();

    }


    /**
     * @return
     */
    @Override
    public boolean isOpen() {
        return isActive();
    }

    /**
     * @return
     */
    @Override
    public boolean isActive() {
        return active;
    }


    /**
     * EventLoop是否为NioEventLoop
     *
     * @param loop
     * @return
     */
    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof NioEventLoop;
    }

    /**
     * @return
     */
    @Override
    protected SocketAddress localAddress0() {
        return parent().localAddress();
    }

    /**
     * @return
     */
    @Override
    protected SocketAddress remoteAddress0() {
        return remoteAddress;
    }


    /**
     * 关闭
     * 在handler中调用ctx.close时，会解发这个方法
     *
     * @throws Exception
     */
    @Override
    protected void doClose() throws Exception {
        active = false;
        parent().removeChildChannel(remoteAddress);
    }


    /**
     * 不支持此项操作
     *
     * @param localAddress
     * @throws Exception
     */
    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    /**
     * 不支持此项操作
     *
     * @throws Exception
     */
    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    /**
     * 开始读取
     *
     * @throws Exception
     */
    @Override
    protected void doBeginRead() throws Exception {
        // nop
    }


    /**
     * 写入数据
     *
     * @param in
     * @throws Exception
     */
    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        BaseTftpPacket tftpPacket = (BaseTftpPacket) in.current();
        ByteBuf byteBuf = tftpPacket.toByteBuf();
        ByteBuffer nioData = byteBuf.internalNioBuffer(byteBuf.readerIndex(),
                byteBuf.readableBytes());
        // 使用 nio channel进行发送
        parent().javaChannel().send(nioData, remoteAddress);
        // 将当前项从输出列表中移除。一定要迁移，否则下一下in.current()会返回同一个对象
        in.remove();
    }


    /**
     * 获取底层Unsafe实例
     *
     * @return
     */
    @Override
    protected UdpServerChildUnsafe newUnsafe() {
        return new UdpServerChildUnsafe();
    }


    /**
     * 此为底层 eventloop注册，selector循环的地方。
     */
    class UdpServerChildUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            safeSetFailure(promise, new UnsupportedOperationException());
        }
    }


}
