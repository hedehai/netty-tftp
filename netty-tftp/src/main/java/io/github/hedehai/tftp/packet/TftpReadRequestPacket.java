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
public class TftpReadRequestPacket extends TftpRequestPacket {



    /**
     * @param filename
     * @param blockSize
     * @param timeout
     * @param transferSize
     */
    public TftpReadRequestPacket(String filename, Integer blockSize, Integer timeout,
                                 Long transferSize) {
        super(TftpOpcode.RRQ);
        this.filename = filename;
        this.blockSize = blockSize;
        this.timeout = timeout;
        this.transferSize = transferSize;
        this.mode = TftpRequestPacket.MODE_OCTET;
    }

    /**
     * @param byteBuf
     */
    public TftpReadRequestPacket(ByteBuf byteBuf) {
        super(byteBuf);
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TftpReadRequestPacket{");
        sb.append("opcode=").append(opcode);
        sb.append(", mode='").append(mode).append('\'');
        sb.append(", filename='").append(filename).append('\'');
        sb.append(", blockSize=").append(blockSize);
        sb.append(", timeout=").append(timeout);
        sb.append(", transferSize=").append(transferSize);
        sb.append('}');
        return sb.toString();
    }
}
