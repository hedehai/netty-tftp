package io.github.hedehai.tftp.util;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author hedehai
 * @date 2018/9/12.
 */
public class ThreadPoolUtils {

    private static ScheduledThreadPoolExecutor threadPool;


    /**
     *
     */
    private ThreadPoolUtils() {
        // nop
    }

    /**
     * 获取线程池实例(3~20个线程)
     *
     * @return
     */
    public static synchronized ScheduledThreadPoolExecutor getInstance() {
        if (threadPool == null) {
            ThreadFactory threadFactory = new ThreadFactory() {
                AtomicInteger threadId = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread1 = new Thread(r);
                    thread1.setName("tftp-thread-" + threadId.getAndIncrement());
                    return thread1;
                }
            };
            threadPool = new ScheduledThreadPoolExecutor(3, threadFactory);
            threadPool.setMaximumPoolSize(20);
        }
        //
        return threadPool;
    }

}
