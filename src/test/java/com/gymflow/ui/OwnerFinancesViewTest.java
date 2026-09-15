package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class OwnerFinancesViewTest {
    @Test
    void formatsMembershipPeriodForReading() {
        assertEquals("15 Sep 2026 – 15 Oct 2026", OwnerFinancesView.formatMembershipPeriod(
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15)));
    }

    @Test
    void calculatesNegativeMonthlyNet() {
        assertEquals(new BigDecimal("-25.50"), OwnerFinancesView.net(
                new BigDecimal("100.00"), new BigDecimal("125.50")));
    }
}
