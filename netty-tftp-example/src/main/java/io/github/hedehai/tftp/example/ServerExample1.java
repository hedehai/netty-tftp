package io.github.hedehai.tftp.example;

import io.github.hedehai.tftp.TftpServer;

import java.io.File;

/**
 * @author hedehai
 * @date 2020/8/9.
 */
public class ServerExample1 {

    public static void main(String[] args) throws Exception {

        File rootDir = new File("workspace/root/");
        TftpServer server = new TftpServer(rootDir);
        server.start();

    }
}
