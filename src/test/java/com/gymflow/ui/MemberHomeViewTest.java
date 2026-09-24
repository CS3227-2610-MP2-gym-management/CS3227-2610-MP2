package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.time.LocalDate;

import com.gymflow.model.Membership;
import com.gymflow.model.MembershipNotice;
import com.gymflow.model.MembershipNoticeState;
import org.junit.jupiter.api.Test;

class MemberHomeViewTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    @Test
    void showsActiveMembershipInsteadOfRenewalGuidance() {
        Membership active = membership(TODAY.minusDays(1), TODAY.plusDays(1), true);
        String summary = MemberHomeView.membershipSummary(notice(MembershipNoticeState.ACTIVE, active), TODAY);

        assertEquals("ACTIVE" + System.lineSeparator() + "Start date: 2026-09-23"
                + System.lineSeparator() + "Expiry date: 2026-09-25", summary);
    }

    @Test
    void showsUpcomingMembershipStartDateInsteadOfRenewalGuidance() {
        Membership upcoming = membership(TODAY.plusDays(3), TODAY.plusDays(33), true);
        String summary = MemberHomeView.membershipSummary(notice(MembershipNoticeState.UPCOMING, upcoming), TODAY);

        assertEquals("Upcoming Membership" + System.lineSeparator() + "Start date: 2026-09-27"
                + System.lineSeparator() + "Expiry date: 2026-10-27", summary);
    }

    @Test
    void directsMembersWithOnlyExpiredOrDeactivatedHistoryToTheGym() {
        String summary = MemberHomeView.membershipSummary(renewalNotice(), TODAY);

        assertEquals("No current or upcoming Membership is recorded. "
                + "Visit the gym in person to purchase or renew your Membership.", summary);
        assertFalse(summary.toLowerCase().contains("online"));
        assertFalse(summary.toLowerCase().contains("contact"));
    }

    @Test
    void directsMembersWithNoMembershipHistoryToTheGym() {
        assertEquals("No current or upcoming Membership is recorded. "
                + "Visit the gym in person to purchase or renew your Membership.",
                MemberHomeView.membershipSummary(renewalNotice(), TODAY));
    }

    @Test
    void preparingRenewalGuidanceDoesNotChangeMembershipHistory() {
        Membership history = membership(TODAY.minusDays(5), TODAY.plusDays(5), false);

        MemberHomeView.membershipSummary(renewalNotice(), TODAY);

        assertFalse(history.active());
    }

    private static Membership membership(LocalDate start, LocalDate expiry, boolean active) {
        return new Membership(1, 1, start, expiry, active, Instant.EPOCH, Instant.EPOCH);
    }

    private static MembershipNotice notice(MembershipNoticeState state, Membership membership) {
        return new MembershipNotice(state, membership);
    }

    private static MembershipNotice renewalNotice() {
        return notice(MembershipNoticeState.RENEWAL_NEEDED, null);
    }
}
