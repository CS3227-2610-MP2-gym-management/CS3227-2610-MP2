package com.gymflow.member;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.gymflow.model.PaymentMethod;

/** Values entered by an Owner during atomic Member onboarding. */
public record CreateMemberRequest(String email, char[] initialPassword,
        String fullName, String phoneNumber, LocalDate dateOfBirth,
        LocalDate membershipStart, LocalDate membershipExpiry,
        BigDecimal paymentAmount, PaymentMethod paymentMethod,
        Instant paidAt, String paymentReference) {
}
