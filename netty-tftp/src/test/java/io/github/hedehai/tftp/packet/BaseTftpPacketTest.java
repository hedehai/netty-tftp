package io.github.hedehai.tftp.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;

/**
 * @author 何德海
 * @date 2021/3/8.
 */
public class BaseTftpPacketTest {


    /**
     * 异常报文
     */
    @Test(expected = IllegalArgumentException.class)
    public void test1() {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(new byte[]{1, 2});
        // 此处会报异常
        BaseTftpPacket packet = new TftpAckPacket(byteBuf);
        System.out.println("packet = " + packet);
    }


    /**
     * 正常报文
     */
    @Test
    public void test2() {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(new byte[]{0, 4, 0, 1});
        BaseTftpPacket packet = new TftpAckPacket(byteBuf);
        System.out.println("packet = " + packet);
        packet.setRemoteAddress(new InetSocketAddress("192.168.1.2", 1024));
        Assert.assertEquals(1024, packet.getRemoteAddress().getPort());
    }


}