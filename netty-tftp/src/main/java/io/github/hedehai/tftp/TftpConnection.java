package io.github.hedehai.tftp;

import io.github.hedehai.tftp.packet.TftpDataPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * @author hedehai
 * @date 2020/8/9.
 */
public class TftpConnection {

    private static final int MAX_BLOCK_NUMBER = 65536;

    private static final Logger LOGGER = LoggerFactory.getLogger(TftpConnection.class);

    private final String id;

    private volatile RandomAccessFile raf;

    private volatile long fileLength;

    private volatile int blockSize;

    private volatile int blockNumber;

    private volatile boolean readFinished = false;

    private volatile byte[] readBuffer;

    private volatile int retries;

    private volatile int timeout;


    /**
     * @param connectionId
     */
    public TftpConnection(String connectionId) {
        this.id = connectionId;
    }


    public String getId() {
        return id;
    }

    public RandomAccessFile getRaf() {
        return raf;
    }

    public void setRaf(RandomAccessFile raf) {
        this.raf = raf;
    }

    public long getFileLength() {
        return fileLength;
    }

    public void setFileLength(long fileLength) {
        this.fileLength = fileLength;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    public int getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(int blockNumber) {
        this.blockNumber = blockNumber;
    }


    public boolean isReadFinished() {
        return readFinished;
    }

    public void setReadFinished(boolean readFinished) {
        this.readFinished = readFinished;
    }

    public byte[] getReadBuffer() {
        return readBuffer;
    }

    public void setReadBuffer(byte[] readBuffer) {
        this.readBuffer = readBuffer;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }


    /**
     * @return
     * @throws Exception
     */
    public TftpDataPacket createDataPacket(int blockNumber) throws IOException {
        TftpDataPacket packet;
        //
        int readCount = raf.read(readBuffer);
        //文件读取完毕
        if (readCount == -1) {
            raf.close();
            readFinished = true;
            // 文件读取完毕后，还需检查文件大小是否等于blockSize的整数倍，
            // 若是，则需要再补发一个空包
            if (fileLength % blockSize == 0) {
                // 空包
                packet = new TftpDataPacket(blockNumber, new byte[]{});
            } else {
                return null;
            }
        } else {
            // 最后一块
            if (readCount < blockSize) {
                byte[] lastBlockData = Arrays.copyOf(readBuffer, readCount);
                packet = new TftpDataPacket(blockNumber, lastBlockData);
            } else {
                // 普通块
                packet = new TftpDataPacket(blockNumber, readBuffer);
            }
        }
        return packet;
    }


    /**
     * 累加blockNumber，当达到65536时从0开始
     */
    public synchronized void increaseBlockNumber() {
        blockNumber++;
        if (blockNumber == MAX_BLOCK_NUMBER) {
            // 变成1，还是变成0？ 应当是从0开始，这个从windows的tftp客户端可以看出来
            blockNumber = 0;
            LOGGER.debug("blockNumber重新开始");
        }
    }


    /**
     * 重置重试次数
     */
    public void resetReties() {
        retries = 0;
    }


    /**
     * 重试次数加1
     */
    public synchronized void increaseReties() {
        retries++;
    }


    /**
     * @param bytes
     * @throws IOException
     */
    public void write(byte[] bytes) throws IOException {
        raf.write(bytes);
    }


    public void closeFile() throws IOException {
        raf.close();
    }

}
