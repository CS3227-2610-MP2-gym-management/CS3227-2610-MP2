package com.gymflow.model;

import java.time.LocalDate;

/** Payment details joined to the Member and Membership shown to an Owner. */
public record PaymentOverview(MemberPayment payment, String memberNumber,
        String memberName, String memberEmail, LocalDate membershipStart,
        LocalDate membershipExpiry) {
}
