package com.dhangofa.networktoggle.util;

/** Central executor for serialized, short-lived telephony work. */
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class AppExecutors {
    
    private static final ThreadPoolExecutor TELEPHONY = new ThreadPoolExecutor(
            0, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
        Thread thread = new Thread(runnable, "NetToggle-telephony");
        thread.setDaemon(true);
        return thread;
    });
    
    private AppExecutors() {}
    
    public static void executeTelephony(Runnable task) {
        if (task != null) {
            TELEPHONY.execute(task);
        }
    }
}
