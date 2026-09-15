package com.gymflow.expense;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.gymflow.model.ExpenseCategory;
import com.gymflow.model.PaymentMethod;

/** Values supplied by an Owner when recording an Expense. */
public record AddExpenseRequest(LocalDate expenseDate, BigDecimal amount,
        PaymentMethod method, ExpenseCategory category, String description) {
}
