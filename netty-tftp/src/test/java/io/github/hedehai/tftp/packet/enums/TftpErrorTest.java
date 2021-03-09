package io.github.hedehai.tftp.packet.enums;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author 何德海
 * @date 2021/3/6.
 */
public class TftpErrorTest {

    /**
     *
     */
    @Test
    public void get() {
        //
        TftpError error0 = TftpError.get(0);
        Assert.assertEquals(TftpError.UNDEFINED, error0);
        //
        TftpError error2 = TftpError.get(2);
        Assert.assertEquals(TftpError.ACCESS_VIOLATION, error2);
        //
        TftpError error5 = TftpError.get(5);
        Assert.assertEquals(TftpError.UNKNOWN_TID, error5);
    }


    /**
     * 非法的错误码输入
     */
    @Test(expected = IllegalArgumentException.class)
    public void get2() {
        TftpError error100 = TftpError.get(100);
        Assert.assertEquals(TftpError.UNDEFINED, error100);
    }

}