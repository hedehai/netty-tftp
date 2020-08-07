package io.github.hedehai.tftp.packet;

import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * * 方向：Client -> Server或 Server -> Client
 * 包结构如下：
 * <pre>
 * 2 bytes     2 bytes      n bytes
 * ----------------------------------
 * | Opcode |   Block #  |   Data     |
 * ----------------------------------
 * </pre>
 *
 * @author hedehai
 * @date 2020/8/9.
 */
public class TftpDataPacket extends BaseTftpPacket {

    /**
     * 0-65536
     */
    private int blockNumber;

    /**
     * 块数据
     */
    private byte[] blockData;


    /**
     * @param byteBuf
     */
    public TftpDataPacket(ByteBuf byteBuf) {
        super(byteBuf);
        this.opcode = TftpOpcode.get(byteBuf.readUnsignedShort());
        this.blockNumber = byteBuf.readUnsignedShort();
        if (byteBuf.readableBytes() > 0) {
            this.blockData = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(blockData);
        } else {
            blockData = new byte[0];
        }
    }


    public TftpDataPacket(int blockNumber, byte[] blockData) {
        super(TftpOpcode.DATA);
        this.blockNumber = blockNumber;
        this.blockData = blockData;
    }


    @Override
    public ByteBuf toByteBuf() {
        ByteBuf byteBuf = Unpooled.buffer(2 + 2 + blockData.length);
        byteBuf.writeBytes(this.opcode.toByteArray());
        byteBuf.writeShort(blockNumber);
        if (blockData.length > 0) {
            byteBuf.writeBytes(blockData);
        }
        return byteBuf;
    }

    public int getBlockNumber() {
        return blockNumber;
    }


    public void setBlockNumber(int blockNumber) {
        this.blockNumber = blockNumber;
    }

    public byte[] getBlockData() {
        return blockData;
    }

    public void setBlockData(byte[] blockData) {
        this.blockData = blockData;
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TftpDataPacket{");
        sb.append("opcode=").append(opcode);
        sb.append(", blockNumber=").append(blockNumber);
        sb.append(", blockData=").append(blockData.length);
        sb.append('}');
        return sb.toString();
    }
}
