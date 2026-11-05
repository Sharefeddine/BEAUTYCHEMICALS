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

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "chemmanager-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    private FxTasks() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        POOL.submit(() -> {
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

    public static void shutdown() {
        POOL.shutdownNow();
    }

    /** Runnable that is allowed to throw checked exceptions. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
