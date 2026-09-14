package com.gymflow.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Owner-visible payment record for a Member. */
public record MemberPayment(Instant paidAt, BigDecimal amount, PaymentMethod method, String reference) {
}
