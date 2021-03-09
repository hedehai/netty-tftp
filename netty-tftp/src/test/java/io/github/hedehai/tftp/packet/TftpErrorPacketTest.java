package io.github.hedehai.tftp.packet;

import io.github.hedehai.tftp.packet.enums.TftpError;
import io.netty.buffer.ByteBuf;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author 何德海
 * @date 2020/8/28.
 */
public class TftpErrorPacketTest {

    @Test
    public void t1() {
        TftpErrorPacket packet1 = new TftpErrorPacket(TftpError.FILE_EXISTS);
        ByteBuf byteBuf = packet1.toByteBuf();
        System.out.println("packet1 = " + packet1);
        // 用byteBuf构建，看能否还原
        TftpErrorPacket packet2 = new TftpErrorPacket(byteBuf);
        System.out.println("packet2 = " + packet2);
        Assert.assertEquals(packet1.getOpcode(), packet2.getOpcode());
        Assert.assertEquals(packet1.getErrorCode(), packet1.getErrorCode());
        Assert.assertEquals(packet1.getErrorMessage(), packet2.getErrorMessage());
    }


}