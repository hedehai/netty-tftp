package io.github.hedehai.tftp.packet;

import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

import static io.github.hedehai.tftp.packet.TftpOptionAckPacket.*;


/**
 * 方向：Client -> Server
 *
 * @author hedehai
 * @date 2020/8/9.
 */
public class TftpRequestPacket extends BaseTftpPacket {

    public static final String MODE_OCTET = "octet";

    /**
     *
     */
    protected String mode;

    /**
     *
     */
    protected String filename;


    /**
     * 协商选项：块大小. block size [0-65535]
     */
    protected Integer blockSize = null;

    /**
     * 协商选项：超时, 单位为秒，[1-255]
     */
    protected Integer timeout = null;

    /**
     * 协商选项：传输大小
     */
    protected Long transferSize = null;


    /**
     * @param opcode
     */
    public TftpRequestPacket(TftpOpcode opcode) {
        super(opcode);
    }

    /**
     * @param byteBuf
     */
    public TftpRequestPacket(ByteBuf byteBuf) {
        super(byteBuf);
        this.opcode = TftpOpcode.get(byteBuf.readUnsignedShort());
        // 将剩余部分读为字符串
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        String str = new String(bytes, StandardCharsets.US_ASCII);
        String[] strArray = str.split("\0");
        // 文件名
        this.filename = strArray[0];
        // 模式
        this.mode = strArray[1];
        // 附加选项
        for (int i = 2; i < strArray.length; i++) {
            switch (strArray[i]) {
                case OPTION_BLOCK_SIZE:
                    this.blockSize = Integer.parseInt(strArray[i + 1]);
                    break;
                case OPTION_TIMEOUT:
                    this.timeout = Integer.parseInt(strArray[i + 1]);
                    break;
                case OPTION_TRANSFER_SIZE:
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
        // 文件名
        byteBuf.writeBytes(filename.getBytes(StandardCharsets.US_ASCII));
        byteBuf.writeByte(0);
        // 模式
        byteBuf.writeBytes(mode.getBytes(StandardCharsets.US_ASCII));
        byteBuf.writeByte(0);
        //
        if (transferSize != null) {
            byteBuf.writeBytes(OPTION_TRANSFER_SIZE.getBytes(StandardCharsets.US_ASCII));
            byteBuf.writeByte(0);
            byteBuf.writeBytes(String.valueOf(transferSize).getBytes(StandardCharsets.US_ASCII));
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
        if (blockSize != null) {
            byteBuf.writeBytes(OPTION_BLOCK_SIZE.getBytes(StandardCharsets.US_ASCII));
            byteBuf.writeByte(0);
            byteBuf.writeBytes(String.valueOf(blockSize).getBytes(StandardCharsets.US_ASCII));
            byteBuf.writeByte(0);
        }
        return byteBuf;
    }


    /**
     * 是否启用了协商
     *
     * @return
     */
    public boolean isNegotiate() {
        // 当以下值不为空时，说明报文是启用了协商的
        return blockSize != null || timeout != null || transferSize != null;
    }


    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getFilename() {
        return filename;
    }


    public Integer getBlockSize() {
        return blockSize;
    }


    public Integer getTimeout() {
        return timeout;
    }


    public Long getTransferSize() {
        return transferSize;
    }


}
