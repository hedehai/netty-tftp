package io.github.hedehai.tftp.packet;

import io.netty.buffer.ByteBuf;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author 何德海
 * @date 2020/8/28.
 */
public class TftpAckPacketTest {

    @Test
    public void t1() {
        TftpAckPacket packet1 = new TftpAckPacket(110);
        ByteBuf byteBuf = packet1.toByteBuf();
        System.out.println("packet1 = " + packet1);

        // 用byteBuf构建，看能否还原
        TftpAckPacket packet2 = new TftpAckPacket(byteBuf);
        System.out.println("packet2 = " + packet2);
        Assert.assertEquals(packet1.getOpcode(), packet2.getOpcode());
        Assert.assertEquals(packet1.getBlockNumber(), packet2.getBlockNumber());

    }

}