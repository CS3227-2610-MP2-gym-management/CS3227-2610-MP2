package com.gymflow.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One immutable operating expense recorded by an Owner. */
public record Expense(long id, LocalDate expenseDate, BigDecimal amount,
        PaymentMethod method, ExpenseCategory category, String description,
        long recordedByAccountId, Instant createdAt) {
}
