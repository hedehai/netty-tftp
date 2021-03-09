package io.github.hedehai.tftp.channel;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;

/**
 * @author 何德海
 * @date 2021/3/8.
 */
public class TftpServerChannelTest {


    @Test(expected = UnsupportedOperationException.class)
    public void test1() throws Exception {
        TftpServerChannel serverChannel = new TftpServerChannel();
        serverChannel.doConnect(null, null);
    }


    @Test(expected = UnsupportedOperationException.class)
    public void test2() {
        TftpServerChannel serverChannel = new TftpServerChannel();
        serverChannel.doDisconnect();
    }


    @Test(expected = UnsupportedOperationException.class)
    public void test3() {
        TftpServerChannel serverChannel = new TftpServerChannel();
        serverChannel.doFinishConnect();
    }


    @Test
    public void test4() throws Exception {
        TftpServerChannel serverChannel = new TftpServerChannel();
        serverChannel.doBind(new InetSocketAddress(69));
        Assert.assertEquals(69, serverChannel.localAddress0().getPort());
    }

}