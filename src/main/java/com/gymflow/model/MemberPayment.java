package com.gymflow.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Owner-visible payment record for a Member. */
public record MemberPayment(long id, long membershipId, BigDecimal amount,
        PaymentMethod method, Instant paidAt, String reference,
        long recordedByAccountId, Instant createdAt) {
}
