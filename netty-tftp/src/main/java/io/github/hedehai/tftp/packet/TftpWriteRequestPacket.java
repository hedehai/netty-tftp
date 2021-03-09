package io.github.hedehai.tftp.packet;

import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.netty.buffer.ByteBuf;

/**
 * 方向：Client -> Server
 * 包结构如下：
 * <pre>
 * 2 bytes     string    1 byte     string   1 byte
 * ------------------------------------------------
 * | Opcode |  Filename  |   0  |    Mode    |   0  |
 * ------------------------------------------------
 * </pre>
 *
 * @author hedehai
 * @date 2020/8/9.
 */
public class TftpWriteRequestPacket extends TftpRequestPacket {


    /**
     * @param filename
     * @param blockSize
     * @param timeout
     * @param transferSize
     */
    public TftpWriteRequestPacket(String filename, Integer blockSize, Integer timeout,
                                  Long transferSize) {
        super(TftpOpcode.WRQ);
        this.filename = filename;
        this.blockSize = blockSize;
        this.timeout = timeout;
        this.transferSize = transferSize;
        this.mode = TftpRequestPacket.MODE_OCTET;
    }

    /**
     * 不带协商的写请求
     *
     * @param filename
     */
    public TftpWriteRequestPacket(String filename) {
        this(filename, null, null, null);
    }


    /**
     * @param byteBuf
     */
    public TftpWriteRequestPacket(ByteBuf byteBuf) {
        super(byteBuf);
    }


    @Override
    public String toString() {
        final StringBuilder sb2 = new StringBuilder("TftpWriteRequestPacket{");
        sb2.append("opcode=").append(opcode);
        sb2.append(", mode='").append(mode).append('\'');
        sb2.append(", filename='").append(filename).append('\'');
        sb2.append(", blockSize=").append(blockSize);
        sb2.append(", timeout=").append(timeout);
        sb2.append(", transferSize=").append(transferSize);
        sb2.append('}');
        return sb2.toString();
    }
}
