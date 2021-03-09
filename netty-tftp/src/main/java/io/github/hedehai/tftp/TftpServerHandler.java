package io.github.hedehai.tftp;


import io.github.hedehai.tftp.packet.BaseTftpPacket;
import io.github.hedehai.tftp.packet.TftpErrorPacket;
import io.github.hedehai.tftp.packet.enums.TftpError;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author hedehai
 * @date 2020/8/9.
 */
public class TftpServerHandler extends SimpleChannelInboundHandler<BaseTftpPacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TftpServerHandler.class);


    private TftpServer tftpServer;


    public TftpServerHandler(TftpServer tftpServer) {
        this.tftpServer = tftpServer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("连接建立, remoteAdress = {}", ctx.channel().remoteAddress());
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("连接关闭, remoteAdress = {}\n", ctx.channel().remoteAddress());
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BaseTftpPacket tftpPacket) {
        LOGGER.debug("收到报文{}", tftpPacket);
        //
        switch (tftpPacket.getOpcode()) {
            case RRQ:
                ctx.pipeline().addLast(new TftpServerReadHandler(tftpServer));
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(tftpPacket);
                break;
            case WRQ:
                ctx.pipeline().addLast(new TftpServerWriteHandler(tftpServer));
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(tftpPacket);
                break;
            // 其它报文为非法操作
            default:
                sendErrorPacket(ctx, TftpError.UNKNOWN_TID);
                break;
        }
    }


    /**
     * @param errorType
     * @param ctx
     */
    private void sendErrorPacket(ChannelHandlerContext ctx, TftpError errorType) {
        TftpErrorPacket errorPacket = new TftpErrorPacket(errorType);
        LOGGER.warn("发送错误报文：{}", errorPacket);
        ctx.writeAndFlush(errorPacket);
        ctx.close();
    }


}

