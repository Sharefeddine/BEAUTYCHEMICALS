package com.chemmanager.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javafx.application.Platform;

/**
 * Small helper that executes blocking work (API calls, exports, report generation) on a
 * background thread and delivers results back on the JavaFX application thread.
 * This is what keeps the UI responsive while data is loading.
 */
public final class FxTasks {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    /**
     * Created lazily and re-created after a {@link #shutdown()}: a stage that is closed and reopened
     * (sign out, a second window, a test that boots the application twice) must not leave the
     * helper with a dead pool, which would silently swallow every later background task.
     */
    private static volatile ExecutorService pool;

    private FxTasks() {
    }

    private static synchronized ExecutorService pool() {
        if (pool == null || pool.isShutdown()) {
            pool = Executors.newFixedThreadPool(4, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "chemmanager-worker-" + COUNTER.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            });
        }
        return pool;
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        pool().submit(() -> {
            try {
                T result = work.call();
                if (onSuccess != null) {
                    Platform.runLater(() -> onSuccess.accept(result));
                }
            } catch (Throwable throwable) {
                if (onError != null) {
                    Platform.runLater(() -> onError.accept(throwable));
                }
            }
        });
    }

    public static void runVoid(ThrowingRunnable work, Runnable onSuccess, Consumer<Throwable> onError) {
        run(() -> {
            work.run();
            return Boolean.TRUE;
        }, ignored -> {
            if (onSuccess != null) {
                onSuccess.run();
            }
        }, onError);
    }

    /** Stops the workers; the next {@link #run} starts a fresh pool instead of failing silently. */
    public static synchronized void shutdown() {
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }

    /** Runnable that is allowed to throw checked exceptions. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
