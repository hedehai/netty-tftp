package io.github.hedehai.tftp.util;

import io.github.hedehai.tftp.packet.*;
import io.github.hedehai.tftp.packet.enums.TftpError;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author 何德海
 * @date 2021/3/6.
 */
public class TftpPacketUtilsTest {

    @Test
    public void test1() {
        //
        TftpAckPacket packet1 = new TftpAckPacket(110);
        Assert.assertNotNull(TftpPacketUtils.create(packet1.toByteBuf()));
        //
        TftpDataPacket packet2 = new TftpDataPacket(10, new byte[]{1, 2, 3, 4, 5});
        Assert.assertNotNull(TftpPacketUtils.create(packet2.toByteBuf()));
        //
        TftpErrorPacket packet3 = new TftpErrorPacket(TftpError.FILE_EXISTS);
        Assert.assertNotNull(TftpPacketUtils.create(packet3.toByteBuf()));
        //
        TftpOptionAckPacket packet4 = new TftpOptionAckPacket(1024, 5, 100_1024L);
        Assert.assertNotNull(TftpPacketUtils.create(packet4.toByteBuf()));
        //
        TftpWriteRequestPacket packet5 = new TftpWriteRequestPacket("123.txt", null, null, null);
        Assert.assertNotNull(TftpPacketUtils.create(packet5.toByteBuf()));
        //
        TftpReadRequestPacket packet6 = new TftpReadRequestPacket("123.txt", 1024, 5, 100_1024L);
        Assert.assertNotNull(TftpPacketUtils.create(packet6.toByteBuf()));
    }

    /**
     * 非法的输入
     */
    @Test(expected = IllegalArgumentException.class)
    public void test2() {
        // invalid package
        ByteBuf byteBuf = Unpooled.buffer(30);
        byteBuf.writeZero(30);
        Assert.assertNull(TftpPacketUtils.create(byteBuf));
    }



}