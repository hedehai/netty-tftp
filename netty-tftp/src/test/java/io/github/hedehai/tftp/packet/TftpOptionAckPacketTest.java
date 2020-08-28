package io.github.hedehai.tftp.packet;

import io.netty.buffer.ByteBuf;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author 何德海
 * @date 2020/8/28.
 */
public class TftpOptionAckPacketTest {

    @Test
    public void t1() {
        TftpOptionAckPacket packet1 = new TftpOptionAckPacket(1024, 5, 100_1024L);
        ByteBuf byteBuf = packet1.toByteBuf();
        System.out.println("packet1 = " + packet1);

        // 用byteBuf构建，看能否还原
        TftpOptionAckPacket packet2 = new TftpOptionAckPacket(byteBuf);
        System.out.println("packet2 = " + packet2);
        Assert.assertEquals(packet1.getOpcode(), packet2.getOpcode());
        Assert.assertEquals(packet1.getBlockSize(), packet2.getBlockSize());
        Assert.assertEquals(packet1.getTimeout(), packet2.getTimeout());
        Assert.assertEquals(packet1.getTransferSize(), packet2.getTransferSize());
    }


}