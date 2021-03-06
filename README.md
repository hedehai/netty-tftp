# netty-tftp
[![Java CI with Maven](https://github.com/hedehai/netty-tftp/actions/workflows/maven-build.yml/badge.svg)](https://github.com/hedehai/netty-tftp/actions/workflows/maven-build.yml)
　　
netty-tftp实现了基于netty的TFTP Server。
  
　　之所以造这个轮子，是因为我在项目中需要集成TFTP Server，但没有找到合适的java库。现有的几个java库有以下的问题：
+ 不支持选项协商，每个包只能传输512B，传输速度慢。
+ 不支持大于32M的文件。

  本项目严格遵守以下rfc规范：
+ https://tools.ietf.org/html/rfc1350
+ https://tools.ietf.org/html/rfc2347
+ https://tools.ietf.org/html/rfc2348
+ https://tools.ietf.org/html/rfc2349

## 特性
+ 可传输大于32M的文件。
+ 支持协商选项blksize。用于配置每次传输多少字节，当块大小为8192B时，会比512B要快16倍。
+ 支持协商选项timeout，用于配置丢包时重试等待的时间。当网络状态良好时，timeout可以配置得低一些，这样传输会快一些。
+ 支持协商选项tsize，用于指示要传输的文件的大小，可以据此实现下载进度功能。
+ 实现了简单的权限功能，可以配置为是否可读、是否可写、是否可覆盖。



## 如何使用

**添加依赖pom.xml**
~~~xml
<dependency>
    <groupId>io.github.hedehai.tftp</groupId>
    <artifactId>netty-tftp</artifactId>
    <version>1.1.0</version>
</dependency>

<repository>
    <id>github</id>
    <name>GitHub OWNER Apache Maven Packages</name>
    <url>https://maven.pkg.github.com/hedehai/netty-tftp</url>
</repository>
~~~

**简单调用**
~~~java
File rootDir = new File("workspace/root/");
TftpServer server = new TftpServer(rootDir);
server.start();
~~~

**高级调用**
~~~java
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
~~~

## 测试过的TFTP Client
+ windows TFTP client
+ 3CDaemon v2.0

PS:关于32M+文件的传输
+ windows TFTP client不支持协商。在非协商模式下，可以传输32M+的文件。
+ 3CDaemon支持协商。在非协商模式下，传输32M+的文件会报错。在协商模式下式，块大小*65536要大于文件大小，才能够传输32M+的文件。
## 待做列表

实现配套的TFTP Client。











