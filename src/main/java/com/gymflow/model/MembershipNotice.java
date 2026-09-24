package com.gymflow.model;

import java.util.Objects;

/** The Membership state a Member-facing screen should emphasize. */
public record MembershipNotice(MembershipNoticeState state, Membership membership) {
    /** Validates that only active and upcoming notices carry a Membership period. */
    public MembershipNotice {
        Objects.requireNonNull(state);
        if ((state == MembershipNoticeState.RENEWAL_NEEDED) != (membership == null)) {
            throw new IllegalArgumentException("Renewal notices have no Membership; other notices require one");
        }
    }
}
