package com.gymflow.monitoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Records basic local startup and unexpected-error diagnostics. */
public final class AppMonitoring {
    private static final int LOG_SIZE_LIMIT = 1_000_000;
    private static final int LOG_FILE_COUNT = 3;
    private static final Logger LOGGER = Logger.getLogger("com.gymflow");
    private static FileHandler handler;
    private static boolean started;
    private static Thread.UncaughtExceptionHandler previousExceptionHandler;

    private AppMonitoring() {
    }

    /** Starts rotating file logging in the supplied directory. */
    public static synchronized void start(Path directory) {
        stop();
        try {
            Files.createDirectories(directory);
            handler = new FileHandler(directory.resolve("gymflow-%g.log").toString(),
                    LOG_SIZE_LIMIT, LOG_FILE_COUNT, true);
            handler.setFormatter(new SimpleFormatter());
            LOGGER.setUseParentHandlers(false);
            LOGGER.addHandler(handler);
            previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(AppMonitoring::recordUncaughtException);
            started = true;
            LOGGER.info("GymFlow started");
        } catch (IOException exception) {
            System.err.println("GymFlow monitoring could not start: "
                    + exception.getClass().getSimpleName());
        }
    }

    /** Flushes and closes the application log. */
    public static synchronized void stop() {
        if (handler != null) {
            LOGGER.removeHandler(handler);
            handler.close();
            handler = null;
        }
        if (started) {
            Thread.setDefaultUncaughtExceptionHandler(previousExceptionHandler);
            previousExceptionHandler = null;
            started = false;
        }
    }

    private static void recordUncaughtException(Thread thread, Throwable error) {
        LOGGER.log(Level.SEVERE, "Unhandled exception on thread {0}: {1}",
                new Object[] {thread.getName(), error.getClass().getName()});
    }
}
