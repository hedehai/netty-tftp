package io.github.hedehai.tftp.packet;

import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.github.hedehai.tftp.util.TftpPacketUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author 何德海
 * @date 2020/8/28.
 */
public class TftpDataPacketTest {

    @Test
    public void test1() {
        TftpDataPacket packet1 = new TftpDataPacket(10, new byte[]{1, 2, 3, 4, 5});
        ByteBuf byteBuf = packet1.toByteBuf();
        System.out.println("packet1 = " + packet1);

        // 用byteBuf构建，看能否还原
        TftpDataPacket packet2 = new TftpDataPacket(byteBuf);
        System.out.println("packet2 = " + packet2);
        Assert.assertEquals(packet1.getOpcode(), packet2.getOpcode());
        Assert.assertEquals(packet1.getBlockNumber(), packet2.getBlockNumber());
        Assert.assertEquals(packet1.getBlockData().length, packet2.getBlockData().length);
    }


    @Test
    public void test2() {
        // 用字节数据来构建
        ByteBuf byteBuf = Unpooled.wrappedBuffer(new byte[]{0, 3, 0, 10});
        BaseTftpPacket packet1 = TftpPacketUtils.create(byteBuf);
        System.out.println("packet1 = " + packet1);
        Assert.assertEquals(TftpOpcode.DATA, packet1.getOpcode());
        Assert.assertEquals(10, ((TftpDataPacket) packet1).getBlockNumber());

        ByteBuf byteBuf2 = packet1.toByteBuf();
        TftpDataPacket packet2 = new TftpDataPacket(byteBuf2);
        Assert.assertEquals(packet1.getOpcode(), packet2.getOpcode());
    }


}