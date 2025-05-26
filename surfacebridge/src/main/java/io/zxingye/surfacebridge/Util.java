package io.zxingye.surfacebridge;

import android.os.Handler;
import android.os.HandlerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class Util {
    public static Handler createWorkHandler(String threadTag) {
        HandlerThread thread = new HandlerThread(threadTag);
        thread.start();
        return new Handler(thread.getLooper());
    }

    public static void runOn(Handler handler, Runnable runnable) {
        runOn(handler, runnable, false);
    }

    public static void runOn(Handler handler, Runnable runnable, boolean sync) {
        if (handler == null || runnable == null || !handler.getLooper().getThread().isAlive()) {
            return;
        }
        if (handler.getLooper().getThread().getId() == Thread.currentThread().getId()) {
            runnable.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);

        boolean isSuccessful = handler.post(() -> {
            try {
                runnable.run();
            } finally {
                latch.countDown();
            }
        });
        if (!isSuccessful) {
            return;
        }
        Thread thread = handler.getLooper().getThread();
        while (sync && !thread.isInterrupted() && thread.isAlive()) {
            try {
                if (latch.await(50, TimeUnit.MILLISECONDS)) {
                    break;
                }
            } catch (InterruptedException ignored) {
                // do nothing
            }
        }
    }
}
