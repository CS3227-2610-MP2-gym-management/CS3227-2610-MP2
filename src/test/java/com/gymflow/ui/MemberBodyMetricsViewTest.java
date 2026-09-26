package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.gymflow.model.BodyMetric;
import org.junit.jupiter.api.Test;

class MemberBodyMetricsViewTest {
    @Test
    void describesWeightChangeAgainstPreviousReading() {
        assertEquals("↓ 0.4 kg since your previous reading", MemberBodyMetricsView.changeText(List.of(
                metric(2, "2026-09-15", "70.1"), metric(1, "2026-09-14", "70.5"))));
        assertEquals("No change since your previous reading", MemberBodyMetricsView.changeText(List.of(
                metric(2, "2026-09-15", "70.5"), metric(1, "2026-09-14", "70.5"))));
        assertEquals("First recorded measurement", MemberBodyMetricsView.changeText(List.of(
                metric(1, "2026-09-14", "70.5"))));
    }

    @Test
    void limitsAndOrdersTrendReadingsForTheSelectedPeriod() {
        LocalDate today = LocalDate.of(2026, 9, 15);
        BodyMetric old = metric(1, "2026-09-01", "71");
        BodyMetric middle = metric(2, "2026-09-10", "70.5");
        BodyMetric newest = metric(3, "2026-09-15", "70.1");

        assertEquals(List.of(middle, newest), MemberBodyMetricsView.recentHistory(
                List.of(newest, old, middle), 7, today));
    }

    @Test
    void filtersCustomChartRangeInclusivelyAndSupportsAllTime() {
        BodyMetric old = metric(1, "2025-12-31", "71");
        BodyMetric start = metric(2, "2026-01-01", "70.5");
        BodyMetric end = metric(3, "2026-03-31", "70.1");

        assertEquals(List.of(start, end), MemberBodyMetricsView.historyBetween(List.of(end, old, start),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));
        assertEquals(List.of(old, start, end), MemberBodyMetricsView.historyBetween(List.of(end, old, start),
                null, null));
    }

    private static BodyMetric metric(long id, String date, String kilograms) {
        return new BodyMetric(id, 1, LocalDate.parse(date), new BigDecimal(kilograms),
                Instant.EPOCH, Instant.EPOCH);
    }
}
