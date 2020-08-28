package io.github.hedehai.tftp.util;

import io.github.hedehai.tftp.packet.*;
import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.netty.buffer.ByteBuf;

/**
 * @author hedehai
 * @date 2020/8/27.
 */
public class TftpPacketUtils {


    /**
     *
     * @param buf
     * @return
     */
    public static BaseTftpPacket create(ByteBuf buf) {
        TftpOpcode opcode = TftpOpcode.get(buf.readUnsignedShort());
        buf.readerIndex(buf.readerIndex() - 2);
        switch (opcode) {
            case RRQ:
                return new TftpReadRequestPacket(buf);
            case WRQ:
                return new TftpWriteRequestPacket(buf);
            case DATA:
                return new TftpDataPacket(buf);
            case ACK:
                return new TftpAckPacket(buf);
            case OACK:
                return new TftpOptionAckPacket(buf);
            case ERROR:
                return new TftpErrorPacket(buf);
            default:
                // nop 不会执行至这里
                return null;
        }

    }

}
