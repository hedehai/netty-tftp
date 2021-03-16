package io.github.hedehai.tftp;

import io.github.hedehai.tftp.packet.*;
import io.github.hedehai.tftp.packet.enums.TftpError;
import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static io.github.hedehai.tftp.packet.enums.TftpError.*;

/**
 * @author 何德海
 * @date 2021/3/6.
 */
public class TftpServerHandlerWriteTest {


    /**
     * @return
     */
    private EmbeddedChannel createChannel() {
        TftpServer tftpServer = new TftpServer(new File("workspace/server/"));
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new TftpServerHandler(tftpServer));
        return channel;
    }


    /**
     * 测试：写请求（不带协商）时，文件不存在时的情况
     * <pre>
     * client                                           server
     * -------------------------------------------------------
     * |2|bar-2.txt|0|octet|0|  -->                          WRQ
     *                                           <--  |4|0|  ACK
     * |5|1|file not found  -->                            ERROR
     * </pre>
     */
    @Test
    public void test1() throws InterruptedException {
        EmbeddedChannel channel = createChannel();
        // 1 请求数据
        TftpWriteRequestPacket packet1 = new TftpWriteRequestPacket("bar-2.txt");
        channel.writeInbound(packet1);
        // 响应数据
        BaseTftpPacket output1 = channel.readOutbound();
        System.out.println("response#1 = " + output1);
        // 期望得到：ACK报文
        Assert.assertEquals(TftpOpcode.ACK, output1.getOpcode());
        Assert.assertEquals(0, ((TftpAckPacket) output1).getBlockNumber());

        // 2 请求数据, ERROR报文，指示客户端文件不存在
        TftpErrorPacket input2 = new TftpErrorPacket(TftpError.FILE_NOT_FOUND);
        channel.writeInbound(input2);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output2 = channel.readOutbound();
        System.out.println("response#2: " + output2);
        // 服务端收到ERROR报文后会关闭连接，不会有响应报文
        Assert.assertNull(output2);
        Assert.assertFalse(channel.isActive());
    }


    /**
     * 测试：写请求（不带协商）时，服务端不允许写入。
     * <pre>
     * client                                           server
     * -------------------------------------------------------
     * |2|bar-2.txt|0|octet|0|  -->                          WRQ
     *                    <-- |5|21|no write permission |  ERROR
     * </pre>
     */
    @Test
    public void test2() throws InterruptedException {
        TftpServer tftpServer = new TftpServer(new File("workspace/server/"));
        // 不允许写
        tftpServer.setAllowWrite(false);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new TftpServerHandler(tftpServer));
        // 1 请求数据
        TftpWriteRequestPacket packet1 = new TftpWriteRequestPacket("bar-2.txt");
        channel.writeInbound(packet1);
        // 响应数据
        BaseTftpPacket output1 = channel.readOutbound();
        System.out.println("response#1 = " + output1);
        // 期望得到：ERROR报文
        Assert.assertEquals(TftpOpcode.ERROR, output1.getOpcode());
        Assert.assertEquals(NO_WRITE_PERMISSION.getErrorCode(),
                ((TftpErrorPacket) output1).getErrorCode());
    }


    /**
     * 测试：写请求（不带协商）时，服务端不允许覆盖写入。
     * <pre>
     * client                                           server
     * -------------------------------------------------------
     * |2|bar-2.txt|0|octet|0|  -->                          WRQ
     *                <-- |5|22|no overwrite permission |  ERROR
     * </pre>
     */
    @Test
    public void test3() throws Exception {
        TftpServer tftpServer = new TftpServer(new File("workspace/server/"));
        // 不允许覆写
        tftpServer.setAllowOverwrite(false);
        // 创建文件
        new File("workspace\\server\\bar-2.txt").createNewFile();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new TftpServerHandler(tftpServer));
        // 1 请求数据
        TftpWriteRequestPacket packet1 = new TftpWriteRequestPacket("bar-2.txt");
        channel.writeInbound(packet1);
        // 响应数据
        BaseTftpPacket output1 = channel.readOutbound();
        System.out.println("response#1 = " + output1);
        // 期望得到：ERROR报文
        Assert.assertEquals(TftpOpcode.ERROR, output1.getOpcode());
        Assert.assertEquals(NO_OVERWRITE_PERMISSION.getErrorCode(),
                ((TftpErrorPacket) output1).getErrorCode());
    }


    /**
     * 测试：写请求（不带协商）时，服务端模式不支持。
     * <pre>
     * client                                           server
     * -------------------------------------------------------
     * |2|bar-2.txt|0|octet|0|  -->                          WRQ
     *                     <-- |5|23|mode not supported |  ERROR
     * </pre>
     */
    @Test
    public void test4() throws Exception {
        EmbeddedChannel channel = createChannel();
        // 1 请求数据
        TftpWriteRequestPacket packet1 = new TftpWriteRequestPacket("bar-2.txt");
        // 设置模式为 netascii 或 mail
        packet1.setMode("netascii");
        channel.writeInbound(packet1);
        // 响应数据
        BaseTftpPacket output1 = channel.readOutbound();
        System.out.println("response#1 = " + output1);
        // 期望得到：ERROR报文
        Assert.assertEquals(TftpOpcode.ERROR, output1.getOpcode());
        Assert.assertEquals(MODE_NOT_SUPPORTED.getErrorCode(),
                ((TftpErrorPacket) output1).getErrorCode());
        // 期望：连接关闭
        Assert.assertFalse(channel.isActive());
    }


    /**
     * 测试写请求（不带协商）时，文件存在时的情况
     * 服务端文件大小：612B
     * <pre>
     * client                                           server
     * -------------------------------------------------------
     * |2|bar.txt|0|octet|0|  -->                            WRQ
     *                                           <--  |4|0|  ACK
     * |3|1| 512 octets of data |                       --> DATA
     * </pre>
     */
    @Test
    public void test10() throws InterruptedException {
        EmbeddedChannel channel = createChannel();
        // 1 请求数据
        TftpWriteRequestPacket packet1 = new TftpWriteRequestPacket("bar.txt");
        channel.writeInbound(packet1);
        // 响应数据
        BaseTftpPacket output1 = channel.readOutbound();
        System.out.println("response#1 = " + output1);
        // 期望得到：ACK报文
        Assert.assertEquals(TftpOpcode.ACK, output1.getOpcode());
        Assert.assertEquals(0, ((TftpAckPacket) output1).getBlockNumber());

        // 2 请求数据。构建DATA报文
        byte[] bytes = new byte[512];
        Arrays.fill(bytes, (byte) '1');
        TftpDataPacket input2 = new TftpDataPacket(1, bytes);
        channel.writeInbound(input2);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output2 = channel.readOutbound();
        System.out.println("response#2 = " + output2);
        // 期望得到：ACK报文
        Assert.assertEquals(TftpOpcode.ACK, output2.getOpcode());
        Assert.assertEquals(1, ((TftpAckPacket) output2).getBlockNumber());

        // 3 请求数据。构建DATA报文，数据只有100字节，不足512
        byte[] bytes3 = new byte[100];
        Arrays.fill(bytes3, (byte) 'a');
        TftpDataPacket input3 = new TftpDataPacket(2, bytes3);
        channel.writeInbound(input3);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output3 = channel.readOutbound();
        System.out.println("response#3 = " + output3);
        // 期望得到：ACK报文
        Assert.assertEquals(TftpOpcode.ACK, output3.getOpcode());
        Assert.assertEquals(2, ((TftpAckPacket) output3).getBlockNumber());
    }


    /**
     * 测试写请求（带协商）时，文件存在时的情况。
     * <pre>
     * client                                                              server
     * ---------------------------------------------------------------------------
     * |2|bar.txt|0|octet|0|blksize|0|600|0|timeout|0|5|0|tsize|0|1000|    --> WRQ
     *                                            <--  |6|blksize|0|1432|0|   OACK
     * |3|1| 600 octets of data |                                         --> DATA
     *                                                             <--  |4|0|  ACK
     * |3|1| 400 octets of data |                                         --> DATA
     * </pre>
     */
    @Test
    public void test11() throws InterruptedException {
        EmbeddedChannel channel = createChannel();
        int blockSize = 600;
        // 1 请求数据
        TftpWriteRequestPacket packet1 = new TftpWriteRequestPacket("bar.txt", blockSize, 5, 1000L);
        channel.writeInbound(packet1);
        // 响应数据
        BaseTftpPacket output1 = channel.readOutbound();
        System.out.println("response#1 = " + output1);
        // 期望得到：OACK报文
        Assert.assertEquals(TftpOpcode.OACK, output1.getOpcode());
        Assert.assertEquals(blockSize, ((TftpOptionAckPacket) output1).getBlockSize().intValue());

        // 2 请求数据。构建DATA报文
        byte[] bytes = new byte[blockSize];
        Arrays.fill(bytes, (byte) '1');
        TftpDataPacket input2 = new TftpDataPacket(1, bytes);
        channel.writeInbound(input2);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output2 = channel.readOutbound();
        System.out.println("response#2 = " + output2);
        // 期望得到：ACK报文
        Assert.assertEquals(TftpOpcode.ACK, output2.getOpcode());
        Assert.assertEquals(1, ((TftpAckPacket) output2).getBlockNumber());

        // 3 请求数据。构建DATA报文，数据只有100字节，不足512
        byte[] bytes3 = new byte[400];
        Arrays.fill(bytes3, (byte) 'a');
        TftpDataPacket input3 = new TftpDataPacket(2, bytes3);
        channel.writeInbound(input3);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output3 = channel.readOutbound();
        System.out.println("response#3 = " + output3);
        // 期望得到：ACK报文
        Assert.assertEquals(TftpOpcode.ACK, output3.getOpcode());
        Assert.assertEquals(2, ((TftpAckPacket) output3).getBlockNumber());
    }

}