package io.github.hedehai.tftp.packet;

import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.netty.buffer.ByteBuf;

import java.net.InetSocketAddress;

/**
 * @author hedehai
 * @date 2020/8/9.
 */
public abstract class BaseTftpPacket {

    private static final int MIN_PACKET_SIZE = 4;

    protected TftpOpcode opcode;

    private InetSocketAddress remoteAddress;

    public BaseTftpPacket(TftpOpcode opcode) {
        this.opcode = opcode;
    }

    public BaseTftpPacket(ByteBuf byteBuf) {
        if (byteBuf.readableBytes() < MIN_PACKET_SIZE) {
            throw new IllegalArgumentException("包长度不够");
        }
    }


    /**
     * 将实例转换成ByteBuf
     *
     * @return
     */
    public abstract ByteBuf toByteBuf();


    public TftpOpcode getOpcode() {
        return opcode;
    }


    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(InetSocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }
}
