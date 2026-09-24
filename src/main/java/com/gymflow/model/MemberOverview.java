package com.gymflow.model;

import java.util.List;

/** Authenticated Member profile and their Membership history. */
public record MemberOverview(Member member, List<Membership> memberships) {
    /** Defensively copies the ordered Membership history. */
    public MemberOverview {
        memberships = List.copyOf(memberships);
    }
}
