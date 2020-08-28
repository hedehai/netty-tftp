package io.github.hedehai.tftp.packet;

import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * 方向：Client -> Server或 Server -> Client
 * 包结构如下：
 * <pre>
 * 2 bytes     2 bytes
 * ---------------------
 * | Opcode |   Block #  |
 * ---------------------
 * </pre>
 *
 * @author hedehai
 * @date 2020/8/9.
 */
public class TftpAckPacket extends BaseTftpPacket {

    /**
     * 0-65535
     */
    private int blockNumber;


    /**
     * @param blockNumber
     */
    public TftpAckPacket(int blockNumber) {
        super(TftpOpcode.ACK);
        this.blockNumber = blockNumber;
    }


    /**
     * @param byteBuf
     */
    public TftpAckPacket(ByteBuf byteBuf) {
        super(byteBuf);
        this.opcode = TftpOpcode.get(byteBuf.readUnsignedShort());
        this.blockNumber = byteBuf.readUnsignedShort();
    }


    @Override
    public ByteBuf toByteBuf() {
        ByteBuf byteBuf = Unpooled.buffer(4);
        byteBuf.writeBytes(this.opcode.toByteArray());
        byteBuf.writeShort(blockNumber);
        return byteBuf;
    }


    public int getBlockNumber() {
        return blockNumber;
    }


    public void setBlockNumber(int blockNumber) {
        this.blockNumber = blockNumber;
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TftpAckPacket{");
        sb.append("opcode=").append(opcode);
        sb.append(", blockNumber=").append(blockNumber);
        sb.append('}');
        return sb.toString();
    }

}
