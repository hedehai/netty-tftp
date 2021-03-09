package io.github.hedehai.tftp.util;

import org.junit.Test;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.Assert.assertEquals;

/**
 * @author 何德海
 * @date 2021/3/6.
 */
public class ThreadPoolUtilsTest {

    @Test
    public void getInstance() {
        ScheduledThreadPoolExecutor pool = ThreadPoolUtils.getInstance();
        assertEquals(3, pool.getCorePoolSize());
        //
        ScheduledThreadPoolExecutor pool2 = ThreadPoolUtils.getInstance();
        assertEquals(pool, pool2);
        //
        pool.execute(() -> {
            System.out.println("run task");
        });
    }
}