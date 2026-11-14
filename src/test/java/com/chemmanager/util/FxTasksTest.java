package com.chemmanager.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The background helper must survive a shutdown: a closed stage is not the end of the process. */
class FxTasksTest {

    @Test
    @DisplayName("a run after shutdown still executes instead of being rejected")
    void runAfterShutdownStillExecutes() throws Exception {
        FxTasks.shutdown();

        CountDownLatch executed = new CountDownLatch(1);
        FxTasks.run(() -> {
            executed.countDown();
            return Boolean.TRUE;
        }, null, null);

        assertTrue(executed.await(5, TimeUnit.SECONDS),
                "the worker pool must be re-created after a shutdown, otherwise later tasks are lost");
    }
}
