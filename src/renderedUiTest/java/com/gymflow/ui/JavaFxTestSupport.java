package com.gymflow.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

/** Starts the JavaFX toolkit once and runs rendered test work on its thread. */
final class JavaFxTestSupport {
    private static final Object LOCK = new Object();
    private static boolean started;

    private JavaFxTestSupport() {
    }

    static <T> T call(Callable<T> action) throws Exception {
        start();
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void start() throws InterruptedException {
        synchronized (LOCK) {
            if (started) {
                return;
            }
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                ready.countDown();
            });
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX toolkit did not start");
            }
            started = true;
        }
    }
}
