package com.gymflow.expense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.model.Account;
import com.gymflow.model.Expense;
import com.gymflow.model.ExpenseCategory;
import com.gymflow.model.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnerExpenseServiceTest {
    @TempDir
    Path directory;
    private Path databaseFile;
    private OwnerExpenseService expenses;
    private Account owner;

    @BeforeEach
    void setUp() {
        databaseFile = directory.resolve("gymflow.db");
        GymFlowDatabase database = new GymFlowDatabase(databaseFile);
        database.initialize();
        AuthenticationService authentication = new AuthenticationService(database);
        owner = authentication.createOwner("owner@example.com", "owner password".toCharArray());
        expenses = new OwnerExpenseService(database);
    }

    @Test
    void addsExpenseWithNormalizedDescriptionAndOwner() {
        Expense expense = expenses.addExpense(new AddExpenseRequest(LocalDate.now(),
                new BigDecimal("125.60"), PaymentMethod.CARD,
                ExpenseCategory.MAINTENANCE, "  Air-con servicing  "), owner.id());

        assertTrue(expense.id() > 0);
        assertEquals(new BigDecimal("125.60"), expense.amount());
        assertEquals("Air-con servicing", expense.description());
        assertEquals(owner.id(), expense.recordedByAccountId());
        assertTrue(expense.createdAt() != null);
    }

    @Test
    void rejectsMissingFutureAndInvalidMonetaryValues() {
        AddExpenseRequest valid = request(LocalDate.now(), new BigDecimal("10.00"));

        assertThrows(IllegalArgumentException.class,
                () -> expenses.addExpense(null, owner.id()));
        assertThrows(IllegalArgumentException.class, () -> expenses.addExpense(
                new AddExpenseRequest(null, valid.amount(), valid.method(), valid.category(), ""), owner.id()));
        assertThrows(IllegalArgumentException.class, () -> expenses.addExpense(
                request(LocalDate.now().plusDays(1), valid.amount()), owner.id()));
        assertThrows(IllegalArgumentException.class, () -> expenses.addExpense(
                request(LocalDate.now(), BigDecimal.ZERO), owner.id()));
        assertThrows(IllegalArgumentException.class, () -> expenses.addExpense(
                request(LocalDate.now(), new BigDecimal("1.001")), owner.id()));
        assertThrows(IllegalArgumentException.class, () -> expenses.addExpense(
                new AddExpenseRequest(valid.expenseDate(), valid.amount(), null, valid.category(), ""), owner.id()));
        assertThrows(IllegalArgumentException.class, () -> expenses.addExpense(
                new AddExpenseRequest(valid.expenseDate(), valid.amount(), valid.method(), null, ""), owner.id()));

        assertTrue(expenses.listExpenses().isEmpty());
    }

    @Test
    void rejectsMissingInactiveAndNonOwnerAccounts() throws Exception {
        AddExpenseRequest request = request(LocalDate.now(), new BigDecimal("10.00"));

        assertThrows(IllegalArgumentException.class, () -> expenses.addExpense(request, 999));
        execute("UPDATE accounts SET is_active = 0 WHERE id = " + owner.id());
        assertThrows(IllegalArgumentException.class, () -> expenses.addExpense(request, owner.id()));
        execute("UPDATE accounts SET is_active = 1, role = 'MEMBER' WHERE id = " + owner.id());
        assertThrows(IllegalArgumentException.class, () -> expenses.addExpense(request, owner.id()));
    }

    @Test
    void listsNewestFirstAndFiltersByCategory() {
        expenses.addExpense(new AddExpenseRequest(LocalDate.now().minusDays(1),
                new BigDecimal("30.00"), PaymentMethod.CASH,
                ExpenseCategory.UTILITIES, "Water"), owner.id());
        expenses.addExpense(new AddExpenseRequest(LocalDate.now(),
                new BigDecimal("40.00"), PaymentMethod.TRANSFER,
                ExpenseCategory.RENT, "Rent"), owner.id());
        expenses.addExpense(new AddExpenseRequest(LocalDate.now(),
                new BigDecimal("20.00"), PaymentMethod.CARD,
                ExpenseCategory.UTILITIES, "Electricity"), owner.id());

        List<Expense> all = expenses.listExpenses();

        assertEquals(List.of("Electricity", "Rent", "Water"),
                all.stream().map(Expense::description).toList());
        assertEquals(List.of("Electricity", "Water"), expenses
                .listExpensesByCategory(ExpenseCategory.UTILITIES).stream()
                .map(Expense::description).toList());
    }

    @Test
    void totalsExpensesAcrossAllRecordedDates() throws Exception {
        LocalDate today = LocalDate.now();
        expenses.addExpense(request(today, new BigDecimal("75.25")), owner.id());
        expenses.addExpense(request(today.withDayOfMonth(1), new BigDecimal("24.75")), owner.id());
        execute("UPDATE expenses SET expense_date = '2020-01-01' WHERE id = 1");

        assertEquals(new BigDecimal("100.00"), expenses.totalExpenses());
    }

    private static AddExpenseRequest request(LocalDate date, BigDecimal amount) {
        return new AddExpenseRequest(date, amount, PaymentMethod.CARD,
                ExpenseCategory.OTHER, "");
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
