package io.github.hedehai.tftp;

import io.github.hedehai.tftp.packet.BaseTftpPacket;
import io.github.hedehai.tftp.packet.TftpAckPacket;
import io.github.hedehai.tftp.packet.TftpErrorPacket;
import io.github.hedehai.tftp.packet.TftpReadRequestPacket;
import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.github.hedehai.tftp.util.TftpPacketUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import static io.github.hedehai.tftp.packet.enums.TftpError.FILE_NOT_FOUND;
import static io.github.hedehai.tftp.packet.enums.TftpError.UNKNOWN_TID;

/**
 * @author 何德海
 * @date 2021/3/8.
 */
public class TftpServerTest {

    /**
     * @return
     */
    private TftpServer createServer() {
        File rootDir = new File("workspace/server/");
        return new TftpServer(rootDir, 69);
    }

    /**
     * 测试关闭后是否能够正常释放端口资源
     *
     * @throws Exception
     */
    @Test
    public void test0() throws Exception {
        File rootDir = new File("workspace/server/");
        TftpServer server1 = new TftpServer(rootDir, 69);
        server1.start();
        server1.stop();

        TftpServer server2 = new TftpServer(rootDir, 69);
        server2.start();
        server2.stop();
        // 如果能够正常启动,能够执行到此
        Assert.assertNotNull(server2);
    }


    /**
     * @throws InterruptedException
     * @throws IOException
     */
    @Test
    public void test1() throws InterruptedException, IOException {
        File rootDir = new File("workspace/server/");
        TftpServer server = new TftpServer(rootDir, 69);
        server.setAllowRead(true);
        server.setAllowWrite(true);
        server.setAllowOverwrite(true);
        server.setMaxRetries(10);
        System.out.println(server);
        //
        Assert.assertNotNull(server);
        Assert.assertTrue(server.isAllowRead());
        Assert.assertTrue(server.isAllowWrite());
        Assert.assertTrue(server.isAllowOverwrite());
        Assert.assertEquals(69, server.getPort());
        Assert.assertEquals(rootDir, server.getRootDir());
        Assert.assertEquals(10, server.getMaxRetries());
    }


    /**
     * 发送RRQ报文，若文件不存在，则服务器会响应错误报文
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void test2() throws IOException, InterruptedException {
        TftpServer server = createServer();
        server.start();
        // 请求。构建读请求报文
        DatagramSocket datagramSocket = new DatagramSocket();
        TftpReadRequestPacket packet1 = new TftpReadRequestPacket("foo-2.txt", null, null, null);
        byte[] bytes = ByteBufUtil.getBytes(packet1.toByteBuf());
        SocketAddress socketAddress = new InetSocketAddress("127.0.0.1", 69);
        DatagramPacket datagramPacket1 = new DatagramPacket(bytes, bytes.length, socketAddress);
        datagramSocket.send(datagramPacket1);
        TimeUnit.SECONDS.sleep(2);
        // 响应
        byte[] buffer = new byte[65536];
        DatagramPacket datagramPacket2 = new DatagramPacket(buffer, buffer.length);
        datagramSocket.receive(datagramPacket2);
        System.out.println("datagramPacket2 = " + datagramPacket2);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(datagramPacket2.getData());
        BaseTftpPacket tftpPacket = TftpPacketUtils.create(byteBuf);
        System.out.println("baseTftpPacket = " + tftpPacket);
        // 期望得到：错误报文
        Assert.assertEquals(TftpOpcode.ERROR, tftpPacket.getOpcode());
        Assert.assertEquals(FILE_NOT_FOUND.getErrorCode(), ((TftpErrorPacket) tftpPacket).getErrorCode());

        // 停止服务器
        server.stop();
    }


    /**
     * 发送DATA报文给报务器，若之前未发送过RRQ报文或WRQ报文，则服务器会响应错误报文
     *
     * @throws InterruptedException
     * @throws IOException
     */
    @Test
    public void test3() throws IOException, InterruptedException {
        TftpServer server = createServer();
        server.start();
        // 请求。构建报文。
        DatagramSocket datagramSocket = new DatagramSocket();
        TftpAckPacket packet1 = new TftpAckPacket(10);
        byte[] bytes = ByteBufUtil.getBytes(packet1.toByteBuf());
        SocketAddress socketAddress = new InetSocketAddress("127.0.0.1", 69);
        DatagramPacket datagramPacket1 = new DatagramPacket(bytes, bytes.length, socketAddress);
        datagramSocket.send(datagramPacket1);
        TimeUnit.SECONDS.sleep(2);
        // 响应
        byte[] buffer = new byte[65536];
        DatagramPacket datagramPacket2 = new DatagramPacket(buffer, buffer.length);
        datagramSocket.receive(datagramPacket2);
        System.out.println("datagramPacket2 = " + datagramPacket2);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(datagramPacket2.getData());
        BaseTftpPacket tftpPacket = TftpPacketUtils.create(byteBuf);
        System.out.println("tftpPacket = " + tftpPacket);
        // 期望得到：错误报文
        Assert.assertEquals(TftpOpcode.ERROR, tftpPacket.getOpcode());
        Assert.assertEquals(UNKNOWN_TID.getErrorCode(), ((TftpErrorPacket) tftpPacket).getErrorCode());

        // 停止服务器
        server.stop();
    }


}