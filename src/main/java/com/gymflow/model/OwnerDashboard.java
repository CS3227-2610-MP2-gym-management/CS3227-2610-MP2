package com.gymflow.model;

import java.math.BigDecimal;
import java.util.List;

/** Database-backed values displayed on the Owner overview. */
public record OwnerDashboard(long totalMembers, long activeMemberships,
        BigDecimal totalIncome, List<MembershipOverview> members) {
}
