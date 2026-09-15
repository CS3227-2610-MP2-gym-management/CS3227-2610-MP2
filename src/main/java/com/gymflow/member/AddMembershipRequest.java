package com.gymflow.member;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.gymflow.model.PaymentMethod;

/** Values entered by an Owner for a new Membership and Payment. */
public record AddMembershipRequest(long memberId, LocalDate startDate,
        LocalDate expiryDate, BigDecimal paymentAmount, PaymentMethod paymentMethod,
        Instant paidAt, String paymentReference) {
}
