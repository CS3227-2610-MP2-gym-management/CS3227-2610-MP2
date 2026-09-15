package com.gymflow.model;

/** Owner-visible Membership with Member identity fields. */
public record MembershipOverview(Membership membership, String memberNumber,
        String memberName, String memberEmail) {
}
