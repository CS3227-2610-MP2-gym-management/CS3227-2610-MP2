package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.gymflow.model.Membership;
import org.junit.jupiter.api.Test;

class MemberHomeViewTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    @Test
    void showsActiveMembershipInsteadOfRenewalGuidance() {
        Membership active = membership(TODAY.minusDays(1), TODAY.plusDays(1), true);
        String summary = MemberHomeView.membershipSummary(List.of(active), TODAY);

        assertEquals("ACTIVE" + System.lineSeparator() + "Start date: 2026-09-23"
                + System.lineSeparator() + "Expiry date: 2026-09-25", summary);
    }

    @Test
    void showsUpcomingMembershipStartDateInsteadOfRenewalGuidance() {
        Membership upcoming = membership(TODAY.plusDays(3), TODAY.plusDays(33), true);
        String summary = MemberHomeView.membershipSummary(List.of(upcoming), TODAY);

        assertEquals("Upcoming Membership" + System.lineSeparator() + "Start date: 2026-09-27"
                + System.lineSeparator() + "Expiry date: 2026-10-27", summary);
    }

    @Test
    void directsMembersWithOnlyExpiredOrDeactivatedHistoryToTheGym() {
        String summary = MemberHomeView.membershipSummary(List.of(
                membership(TODAY.minusDays(40), TODAY.minusDays(10), true),
                membership(TODAY.minusDays(5), TODAY.plusDays(5), false)), TODAY);

        assertEquals("No current or upcoming Membership is recorded. "
                + "Visit the gym in person to purchase or renew your Membership.", summary);
        assertFalse(summary.toLowerCase().contains("online"));
        assertFalse(summary.toLowerCase().contains("contact"));
    }

    @Test
    void directsMembersWithNoMembershipHistoryToTheGym() {
        assertEquals("No current or upcoming Membership is recorded. "
                + "Visit the gym in person to purchase or renew your Membership.",
                MemberHomeView.membershipSummary(List.of(), TODAY));
    }

    @Test
    void preparingRenewalGuidanceDoesNotChangeMembershipHistory() {
        List<Membership> history = new ArrayList<>();

        MemberHomeView.membershipSummary(history, TODAY);

        assertEquals(List.of(), history);
    }

    private static Membership membership(LocalDate start, LocalDate expiry, boolean active) {
        return new Membership(1, 1, start, expiry, active, Instant.EPOCH, Instant.EPOCH);
    }
}
