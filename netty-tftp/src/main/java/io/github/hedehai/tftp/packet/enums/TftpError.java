/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.hedehai.tftp.packet.enums;

/**
 * https://tools.ietf.org/html/rfc1350
 *
 * @author hedehai
 * @date 2020/8/9.
 */
public enum TftpError {

    /**
     * 未定义
     */
    UNDEFINED(0, "undefined"),

    /**
     * 找不到文件
     */
    FILE_NOT_FOUND(1, "file not found"),

    /**
     * 非法访问
     */
    ACCESS_VIOLATION(2, "access violation"),

    /**
     * 磁盘满或者超过分配的配额
     */
    OUT_OF_SPACE(3, "out of space"),

    /**
     * 非法操作
     */
    ILLEGAL_OPERATION(4, "illegal operation"),

    /**
     * 未知的传输ID
     */
    UNKNOWN_TID(5, "unknown tid"),

    /**
     * 文件已存在
     */
    FILE_EXISTS(6, "file exists"),

    /**
     * 没有此用户
     */
    NO_SUCH_USER(7, "no such user"),

    /**
     * 协商失败
     */
    NEGOTIATE_FAIL(8, "negotiate fail"),


    /**
     * 自定义: 无读取权限
     */
    NO_READ_PERMISSION(20, "no read permission"),


    /**
     * 自定义: 无写入权限
     */
    NO_WRITE_PERMISSION(21, "no write permission"),

    /**
     * 自定义: 无覆盖权限
     */
    NO_OVERWRITE_PERMISSION(22, "no overwrite permission"),

    /**
     * 自定义: 无覆盖权限
     */
    MODE_NOT_SUPPORTED(23, "mode not supported");


    private final int errorCode;

    private final String errorMessage;


    TftpError(int errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static TftpError get(int errorCode) {
        for (TftpError error : TftpError.values()) {
            if (error.errorCode == errorCode) {
                return error;
            }
        }
        throw new IllegalArgumentException("No such Error : " + errorCode);
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }


}
