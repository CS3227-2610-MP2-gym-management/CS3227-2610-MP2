package com.gymflow.model;

import java.time.Instant;
import java.time.LocalDate;

/** One purchased period of gym access. */
public record Membership(long id, long memberId, LocalDate startDate,
        LocalDate expiryDate, boolean active, Instant createdAt, Instant updatedAt) {
    /** Derives the display state on the supplied date. */
    public MembershipStatus status(LocalDate date) {
        if (!active) {
            return MembershipStatus.DEACTIVATED;
        } else if (date.isBefore(startDate)) {
            return MembershipStatus.UPCOMING;
        } else if (date.isAfter(expiryDate)) {
            return MembershipStatus.EXPIRED;
        }
        return MembershipStatus.ACTIVE;
    }
}
