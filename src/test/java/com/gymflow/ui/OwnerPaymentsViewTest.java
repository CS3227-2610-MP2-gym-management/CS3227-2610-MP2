package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class OwnerPaymentsViewTest {
    @Test
    void formatsMembershipPeriodForReading() {
        assertEquals("15 Sep 2026 – 15 Oct 2026", OwnerPaymentsView.formatMembershipPeriod(
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15)));
    }
}
