package io.github.hedehai.tftp.packet;

import io.netty.buffer.ByteBuf;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author 何德海
 * @date 2020/8/28.
 */
public class TftpReadRequestPacketTest {


    @Test
    public void t1() {
        TftpReadRequestPacket packet1 = new TftpReadRequestPacket("123.txt", 1024, 5, 100_1024L);
        ByteBuf byteBuf = packet1.toByteBuf();
        System.out.println("packet1 = " + packet1);
        // 用byteBuf构建，看能否还原
        TftpReadRequestPacket packet2 = new TftpReadRequestPacket(byteBuf);
        System.out.println("packet2 = " + packet2);
        Assert.assertEquals(packet1.getOpcode(), packet2.getOpcode());
        Assert.assertEquals(packet1.getFilename(), packet2.getFilename());
        Assert.assertEquals(packet1.getMode(), packet2.getMode());
        Assert.assertEquals(packet1.getBlockSize(), packet2.getBlockSize());
        Assert.assertEquals(packet1.getTimeout(), packet2.getTimeout());
        Assert.assertEquals(packet1.getTransferSize(), packet2.getTransferSize());
    }



    @Test
    public void t2() {
        TftpWriteRequestPacket packet1 = new TftpWriteRequestPacket("123.txt", null, null, null);
        ByteBuf byteBuf = packet1.toByteBuf();
        System.out.println("packet1 = " + packet1);
        // 用byteBuf构建，看能否还原
        TftpWriteRequestPacket packet2 = new TftpWriteRequestPacket(byteBuf);
        System.out.println("packet2 = " + packet2);
        Assert.assertEquals(packet1.getOpcode(), packet2.getOpcode());
        Assert.assertEquals(packet1.getFilename(), packet2.getFilename());
        Assert.assertEquals(packet1.getMode(), packet2.getMode());
        Assert.assertEquals(packet1.getBlockSize(), packet2.getBlockSize());
        Assert.assertEquals(packet1.getTimeout(), packet2.getTimeout());
        Assert.assertEquals(packet1.getTransferSize(), packet2.getTransferSize());
    }

}