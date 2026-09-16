package com.gymflow.monitoring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppMonitoringTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void recordsStartupAndSanitizedUncaughtErrors() throws Exception {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        try {
            AppMonitoring.start(temporaryDirectory);
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(
                    Thread.currentThread(), new IllegalStateException("secret form value"));
        } finally {
            AppMonitoring.stop();
        }

        assertSame(previous, Thread.getDefaultUncaughtExceptionHandler());
        String logs = Files.readString(temporaryDirectory.resolve("gymflow-0.log"));
        assertTrue(logs.contains("GymFlow started"));
        assertTrue(logs.contains("java.lang.IllegalStateException"));
        assertFalse(logs.contains("secret form value"));
    }
}
