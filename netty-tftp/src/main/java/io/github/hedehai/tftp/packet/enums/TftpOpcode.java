/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.hedehai.tftp.packet.enums;


/**
 * https://tools.ietf.org/html/rfc1350
 * https://tools.ietf.org/html/rfc2347
 *
 * @author hedehai
 * @date 2020/8/9.
 */
public enum TftpOpcode {

    /**
     *
     */
    RRQ(1, "Read request"),

    /**
     *
     */
    WRQ(2, "Write request"),

    /**
     *
     */
    DATA(3, "Data"),

    /**
     *
     */
    ACK(4, "Acknowledgment"),

    /**
     *
     */
    ERROR(5, "Error"),

    /**
     *
     */
    OACK(6, "Ack with options");


    private final int code;

    private final String text;


    TftpOpcode(int code, String text) {
        this.code = code;
        this.text = text;
    }

    public static TftpOpcode get(int code) {
        for (TftpOpcode opcode : TftpOpcode.values()) {
            if (opcode.getCode() == code) {
                return opcode;
            }
        }
        throw new IllegalArgumentException("No such TFTP opcode " + code);
    }

    public long getCode() {
        return code;
    }

    public String getText() {
        return text;
    }

    public byte[] toByteArray() {
        return new byte[]{0, (byte) code};
    }

    @Override
    public String toString() {
        return name() + "(" + getCode() + ", " + getText() + ")";
    }


}
