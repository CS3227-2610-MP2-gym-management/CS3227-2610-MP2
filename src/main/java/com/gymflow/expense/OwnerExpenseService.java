package com.gymflow.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.OwnerExpenseStore;
import com.gymflow.model.Expense;
import com.gymflow.model.ExpenseCategory;

/** Validates and retrieves Owner-managed operating Expenses. */
public final class OwnerExpenseService {
    private final OwnerExpenseStore expenses;

    /** Creates an Expense service backed by the supplied database. */
    public OwnerExpenseService(GymFlowDatabase database) {
        expenses = new OwnerExpenseStore(database);
    }

    /** Validates and records one immutable Expense. */
    public Expense addExpense(AddExpenseRequest request, long ownerAccountId) {
        validate(request);
        return expenses.add(request, normalizeDescription(request.description()), ownerAccountId);
    }

    /** Lists all Expenses newest first. */
    public List<Expense> listExpenses() {
        return expenses.list(null);
    }

    /** Lists Expenses belonging to one category, newest first. */
    public List<Expense> listExpensesByCategory(ExpenseCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("Expense category is required");
        }
        return expenses.list(category);
    }

    /** Totals Expenses in the computer's current calendar month. */
    public BigDecimal expensesThisMonth() {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        return expenses.total(start, start.plusMonths(1));
    }

    private static void validate(AddExpenseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Expense details are required");
        }
        if (request.expenseDate() == null) {
            throw new IllegalArgumentException("Expense date is required");
        }
        if (request.expenseDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Expense date cannot be in the future");
        }
        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Expense amount must be positive with at most two decimal places");
        }
        try {
            amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Expense amount is too large", exception);
        }
        if (request.method() == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        if (request.category() == null) {
            throw new IllegalArgumentException("Expense category is required");
        }
    }

    private static String normalizeDescription(String description) {
        return description == null ? "" : description.trim();
    }
}
