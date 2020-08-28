package io.github.hedehai.tftp.packet;

import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

/**
 * 方向：Server -> Client
 * 包结构如下：
 * <pre>
 * +-------+---~~---+---+---~~---+---+---~~---+---+---~~---+---+
 * |  opc  |  opt1  | 0 | value1 | 0 |  optN  | 0 | valueN | 0 |
 * +-------+---~~---+---+---~~---+---+---~~---+---+---~~---+---+
 *  </pre>
 * see: https://tools.ietf.org/html/rfc2347 <p/>
 * see https://tools.ietf.org/html/rfc2348 <p/>
 * see https://tools.ietf.org/html/rfc2349 <p/>
 *
 * @author hedehai
 * @date 2020/8/9.
 */
public class TftpOptionAckPacket extends BaseTftpPacket {

    /**
     * rfc2348中定义。
     */
    public static final String OPTION_BLOCK_SIZE = "blksize";

    /**
     * rfc2349中定义。
     */
    public static final String OPTION_TIMEOUT = "timeout";


    /**
     * rfc2349中定义。
     */
    public static final String OPTION_TRANSFER_SIZE = "tsize";


    /**
     * 块大小
     */
    private Integer blockSize;


    /**
     * 超时时间，单位是秒
     */
    private Integer timeout;

    /**
     * 传输大小
     */
    private Long transferSize;


    /**
     * @param blockSize
     * @param timeout
     * @param transferSize
     */
    public TftpOptionAckPacket(Integer blockSize, Integer timeout, Long transferSize) {
        super(TftpOpcode.OACK);
        //
        this.blockSize = blockSize;
        this.timeout = timeout;
        this.transferSize = transferSize;
    }


    /**
     *
     * @param byteBuf
     */
    public TftpOptionAckPacket(ByteBuf byteBuf) {
        super(byteBuf);
        this.opcode = TftpOpcode.get(byteBuf.readUnsignedShort());
        // 将剩余部分读为字符串
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        String str = new String(bytes, StandardCharsets.US_ASCII);
        String[] strArray = str.split("\0");
        // 附加选项
        for (int i = 0; i < strArray.length; i++) {
            switch (strArray[i]) {
                case TftpOptionAckPacket.OPTION_BLOCK_SIZE:
                    this.blockSize = Integer.parseInt(strArray[i + 1]);
                    break;
                case TftpOptionAckPacket.OPTION_TIMEOUT:
                    this.timeout = Integer.parseInt(strArray[i + 1]);
                    break;
                case TftpOptionAckPacket.OPTION_TRANSFER_SIZE:
                    this.transferSize = Long.parseLong(strArray[i + 1]);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public ByteBuf toByteBuf() {
        ByteBuf byteBuf = Unpooled.buffer(30);
        byteBuf.writeBytes(this.opcode.toByteArray());
        //
        if (blockSize != null) {
            byteBuf.writeBytes(OPTION_BLOCK_SIZE.getBytes(StandardCharsets.US_ASCII));
            byteBuf.writeByte(0);
            byteBuf.writeBytes(String.valueOf(blockSize).getBytes(StandardCharsets.US_ASCII));
            byteBuf.writeByte(0);
        }
        //
        if (timeout != null) {
            byteBuf.writeBytes(OPTION_TIMEOUT.getBytes(StandardCharsets.US_ASCII));
            byteBuf.writeByte(0);
            byteBuf.writeBytes(String.valueOf(timeout).getBytes(StandardCharsets.US_ASCII));
            byteBuf.writeByte(0);
        }
        //
        if (transferSize != null) {
            byteBuf.writeBytes(OPTION_TRANSFER_SIZE.getBytes(StandardCharsets.US_ASCII));
            byteBuf.writeByte(0);
            byteBuf.writeBytes(String.valueOf(transferSize).getBytes(StandardCharsets.US_ASCII));
            byteBuf.writeByte(0);
        }
        return byteBuf;
    }


    public Integer getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(Integer blockSize) {
        this.blockSize = blockSize;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Long getTransferSize() {
        return transferSize;
    }

    public void setTransferSize(Long transferSize) {
        this.transferSize = transferSize;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TftpOptionAckPacket{");
        sb.append("opcode=").append(opcode);
        sb.append(", blockSize=").append(blockSize);
        sb.append(", transferSize=").append(transferSize);
        sb.append(", timeout=").append(timeout);
        sb.append('}');
        return sb.toString();
    }


}
