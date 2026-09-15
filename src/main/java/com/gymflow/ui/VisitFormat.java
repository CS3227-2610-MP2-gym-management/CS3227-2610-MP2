package com.gymflow.ui;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Shared display formatting for Owner-visible Visits. */
final class VisitFormat {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").withZone(ZoneId.systemDefault());

    private VisitFormat() {
    }

    static String entryTime(Instant enteredAt) {
        return TIME.format(enteredAt);
    }

    static String exitTime(Instant exitedAt) {
        return exitedAt == null ? "Currently inside" : TIME.format(exitedAt);
    }

    static String duration(Instant enteredAt, Instant exitedAt) {
        if (exitedAt == null) {
            return "Ongoing";
        }
        long minutes = Duration.between(enteredAt, exitedAt).toMinutes();
        long hours = minutes / 60;
        return hours == 0 ? minutes + " min" : hours + " hr " + minutes % 60 + " min";
    }
}
