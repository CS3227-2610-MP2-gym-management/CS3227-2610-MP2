package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class VisitFormatTest {
    @Test
    void formatsCompletedAndOngoingVisits() {
        Instant entered = Instant.parse("2026-09-15T01:00:00Z");
        Instant exited = Instant.parse("2026-09-15T02:35:00Z");

        assertEquals("1 hr 35 min", VisitFormat.duration(entered, exited));
        assertEquals("35 min", VisitFormat.duration(entered, entered.plusSeconds(35 * 60)));
        assertEquals("0 min", VisitFormat.duration(entered, entered.plusSeconds(30)));
        assertEquals("Ongoing", VisitFormat.duration(entered, null));
        assertEquals("Currently inside", VisitFormat.exitTime(null));
    }
}
