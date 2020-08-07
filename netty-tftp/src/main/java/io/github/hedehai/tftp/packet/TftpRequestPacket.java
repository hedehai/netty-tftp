package io.github.hedehai.tftp.packet;

import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

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
    protected ByteBuf toByteBuf() {
        return null;
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

    public void setFilename(String filename) {
        this.filename = filename;
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


}
