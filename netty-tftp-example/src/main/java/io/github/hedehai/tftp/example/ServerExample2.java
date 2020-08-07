package io.github.hedehai.tftp.example;

import io.github.hedehai.tftp.TftpServer;

import java.io.File;

/**
 * @author hedehai
 * @date 2020/8/9.
 */
public class ServerExample2 {

    public static void main(String[] args) throws Exception {

        File rootDir = new File("workspace/root/");

        TftpServer server = new TftpServer(rootDir);
        // 高级的配置
        // 允许读取
        server.setAllowRead(true);
        // 不允许写入
        server.setAllowWrite(false);
        // 不允许覆盖
        server.setAllowOverwrite(false);
        server.start();


    }
}
