package io.github.hedehai.tftp;

import io.github.hedehai.tftp.packet.*;
import io.github.hedehai.tftp.packet.enums.TftpError;
import io.github.hedehai.tftp.packet.enums.TftpOpcode;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static io.github.hedehai.tftp.packet.enums.TftpError.MODE_NOT_SUPPORTED;
import static io.github.hedehai.tftp.packet.enums.TftpError.NO_READ_PERMISSION;

/**
 * @author 何德海
 * @date 2021/3/6.
 */
public class TftpServerHandlerReadTest {


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
     * 测试读请求（不带协商）时，文件找不到时的情况
     * <pre>
     * client                                           server
     * -------------------------------------------------------
     * |1|foo-2.txt|0|octet|0|  -->                          RRQ
     *                          <--  |5|1|file not found   ERROR
     * </pre>
     */
    @Test
    public void test1() throws InterruptedException {
        EmbeddedChannel channel = createChannel();
        // 请求数据
        TftpReadRequestPacket input1 = new TftpReadRequestPacket("foo-2.txt", null, null, null);
        channel.writeInbound(input1);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output1 = channel.readOutbound();
        System.out.println("response#1" + output1);
        // 期望得到：ERROR报文
        Assert.assertEquals(TftpOpcode.ERROR, output1.getOpcode());
        // 期望得到：文件找不到的错误码
        TftpErrorPacket errorPacket = (TftpErrorPacket) output1;
        Assert.assertEquals(TftpError.FILE_NOT_FOUND.getErrorCode(), errorPacket.getErrorCode());
    }


    /**
     * 测试：读请求（不带协商）时，服务端不允许读取。
     * <pre>
     * client                                           server
     * -------------------------------------------------------
     * |1|foo.txt|0|octet|0|  -->                            RRQ
     *                     <-- |5|20|no read permission |  ERROR
     * </pre>
     */
    @Test
    public void test2() throws InterruptedException {
        TftpServer tftpServer = new TftpServer(new File("workspace/server/"));
        // 不允许写
        tftpServer.setAllowRead(false);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new TftpServerHandler(tftpServer));
        // 1 请求数据
        TftpReadRequestPacket packet1 = new TftpReadRequestPacket("foo.txt");
        channel.writeInbound(packet1);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output1 = channel.readOutbound();
        System.out.println("response#1 = " + output1);
        // 期望得到：ERROR报文
        Assert.assertEquals(TftpOpcode.ERROR, output1.getOpcode());
        Assert.assertEquals(NO_READ_PERMISSION.getErrorCode(),
                ((TftpErrorPacket) output1).getErrorCode());
    }


    /**
     * 测试：读请求（不带协商）时，服务端模式不支持。
     * <pre>
     * client                                           server
     * -------------------------------------------------------
     * |1|foo-2.txt|0|octet|0|  -->                          RRQ
     *                     <-- |5|23|mode not supported |  ERROR
     * </pre>
     */
    @Test
    public void test3() throws Exception {
        EmbeddedChannel channel = createChannel();
        // 1 请求数据
        TftpReadRequestPacket packet1 = new TftpReadRequestPacket("foo-2.txt");
        // 设置模式为 netascii 或 mail
        packet1.setMode("mail");
        channel.writeInbound(packet1);
        // 响应数据
        BaseTftpPacket output1 = channel.readOutbound();
        System.out.println("response#1 = " + output1);
        // 期望得到：ERROR报文
        Assert.assertEquals(TftpOpcode.ERROR, output1.getOpcode());
        Assert.assertEquals(MODE_NOT_SUPPORTED.getErrorCode(),
                ((TftpErrorPacket) output1).getErrorCode());
    }


    /**
     * 测试读请求(带协商)时，文件找得到的情况
     *
     * <pre>
     * client                                           server
     * -------------------------------------------------------
     * |1|foo.txt|0|octet|0|blksize|0|1432|0|  -->               RRQ
     *                              <--  |6|blksize|0|1432|0|   OACK
     * |4|0|  -->                                                ACK
     *                       <--  |3|1| 1432 octets of data |   DATA
     * |4|1|  -->                                                ACK
     *                       <--  |3|2| 1432 octets of data |   DATA
     * |4|2|  -->                                                ACK
     *                       <--  |3|3|<1432 octets of data |   DATA
     * ...
     * </pre>
     *
     * @see <href>https://tools.ietf.org/html/rfc2347</href>
     */
    @Test
    public void test10() throws InterruptedException {
        EmbeddedChannel channel = createChannel();
        int blockSize = 1432;

        // 1 请求数据，RRQ报文
        TftpReadRequestPacket input1 = new TftpReadRequestPacket("foo.txt", blockSize, null, null);
        channel.writeInbound(input1);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output1 = channel.readOutbound();
        System.out.println("response#1: " + output1);
        // 期望得到：OACK报文
        Assert.assertEquals(TftpOpcode.OACK, output1.getOpcode());
        Assert.assertEquals(blockSize, ((TftpOptionAckPacket) output1).getBlockSize().intValue());

        // 2 请求数据, ACK报文
        TftpAckPacket input2 = new TftpAckPacket(0);
        channel.writeInbound(input2);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output2 = channel.readOutbound();
        System.out.println("response#2: " + output2);
        // 期望得到：DATA报文
        Assert.assertEquals(TftpOpcode.DATA, output2.getOpcode());
        Assert.assertEquals(1, ((TftpDataPacket) output2).getBlockNumber());
        Assert.assertEquals(blockSize, ((TftpDataPacket) output2).getBlockData().length);

        // 3 请求数据, ACK报文
        TftpAckPacket input3 = new TftpAckPacket(1);
        channel.writeInbound(input3);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output3 = channel.readOutbound();
        System.out.println("response#3: " + output3);
        // 期望得到：DATA报文
        Assert.assertEquals(TftpOpcode.DATA, output3.getOpcode());
        Assert.assertEquals(2, ((TftpDataPacket) output3).getBlockNumber());

        // 3 请求数据, ACK报文
        TftpAckPacket input4 = new TftpAckPacket(2);
        channel.writeInbound(input4);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output4 = channel.readOutbound();
        System.out.println("response#4: " + output4);
        // 期望得到：DATA报文
        Assert.assertEquals(TftpOpcode.DATA, output4.getOpcode());
        Assert.assertEquals(3, ((TftpDataPacket) output4).getBlockNumber());
    }


    /**
     * 测试读请求(不带协商)时，文件找得到的情况
     * <pre>
     * client                                           server
     * -------------------------------------------------------
     * |1|foo.txt|0|octet|0|  -->                                RRQ
     *                        <--  |3|1| 512 octets of data |   DATA
     * |4|1|  -->                                                ACK
     *                        <--  |3|2| 512 octets of data |   DATA
     * |4|2|  -->                                                ACK
     *                        <--  |3|3| 512 octets of data |   DATA
     * ...
     * </pre>
     *
     * @see <href>https://tools.ietf.org/html/rfc1350</href>
     */
    @Test
    public void test11() throws InterruptedException {
        EmbeddedChannel channel = createChannel();

        // 1 请求数据，RRQ报文
        TftpReadRequestPacket input1 = new TftpReadRequestPacket("foo.txt");
        channel.writeInbound(input1);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output1 = channel.readOutbound();
        System.out.println("response#1: " + output1);
        // 期望得到：DATA报文
        Assert.assertEquals(TftpOpcode.DATA, output1.getOpcode());
        Assert.assertEquals(1, ((TftpDataPacket) output1).getBlockNumber());
        Assert.assertEquals(512, ((TftpDataPacket) output1).getBlockData().length);

        // 2 请求数据, ACK报文
        TftpAckPacket input2 = new TftpAckPacket(1);
        channel.writeInbound(input2);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output2 = channel.readOutbound();
        System.out.println("response#2: " + output2);
        // 期望得到：DATA报文
        Assert.assertEquals(TftpOpcode.DATA, output2.getOpcode());
        Assert.assertEquals(2, ((TftpDataPacket) output2).getBlockNumber());

        // 3 请求数据, ACK报文
        TftpAckPacket input3 = new TftpAckPacket(2);
        channel.writeInbound(input3);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output3 = channel.readOutbound();
        System.out.println("response#3: " + output3);
        // 期望得到：DATA报文
        Assert.assertEquals(TftpOpcode.DATA, output3.getOpcode());
        Assert.assertEquals(3, ((TftpDataPacket) output3).getBlockNumber());
    }


    /**
     * todo 测试读请求(不带协商)时，模拟丢包
     * <pre>
     * client                                           server
     * -------------------------------------------------------
     * |1|foo.txt|0|octet|0|  -->                                RRQ
     *                        <--  |3|1| 512 octets of data |   DATA
     * |4|1|  -->                                                ACK
     *                        <--  |3|2| 512 octets of data |   DATA
     * |4|2|  -->                                                ACK
     *                        <--  |3|3| 512 octets of data |   DATA
     * ...
     * </pre>
     *
     * @see <href>https://tools.ietf.org/html/rfc1350</href>
     */
    @Test
    public void test12() throws InterruptedException {
        EmbeddedChannel channel = createChannel();

        // 1 请求数据，RRQ报文
        TftpReadRequestPacket input1 = new TftpReadRequestPacket("foo.txt");
        channel.writeInbound(input1);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output1 = channel.readOutbound();
        System.out.println("response#1: " + output1);
        // 期望得到：DATA报文
        Assert.assertEquals(TftpOpcode.DATA, output1.getOpcode());
        Assert.assertEquals(1, ((TftpDataPacket) output1).getBlockNumber());
        Assert.assertEquals(512, ((TftpDataPacket) output1).getBlockData().length);

        // 2 请求数据, ACK报文。
        TftpAckPacket input2 = new TftpAckPacket(1);
        channel.writeInbound(input2);
        TimeUnit.MILLISECONDS.sleep(200);
        // 响应数据
        BaseTftpPacket output2 = channel.readOutbound();
        System.out.println("response#2: " + output2);
        // 期望得到：DATA报文
        Assert.assertEquals(TftpOpcode.DATA, output2.getOpcode());
        Assert.assertEquals(2, ((TftpDataPacket) output2).getBlockNumber());

        // 模拟网络出了问题：假定没有收到编号为2的DATA报文，客户端发送编号为1的ACK，
        // 这会让服务端发送上一个DATA包(编号为2)。

        // 3 请求数据, ACK报文
        TftpAckPacket input3 = new TftpAckPacket(1);
        channel.writeInbound(input3);
        TimeUnit.MILLISECONDS.sleep(3000);
        // 响应数据
        BaseTftpPacket output3 = channel.readOutbound();
        System.out.println("response#3: " + output3);
        // 期望得到：DATA报文
        Assert.assertEquals(TftpOpcode.DATA, output3.getOpcode());
        Assert.assertEquals(2, ((TftpDataPacket) output3).getBlockNumber());
    }

}