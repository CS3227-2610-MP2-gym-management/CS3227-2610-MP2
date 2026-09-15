package com.gymflow.data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.gymflow.expense.AddExpenseRequest;
import com.gymflow.model.Expense;
import com.gymflow.model.ExpenseCategory;
import com.gymflow.model.PaymentMethod;

/** Persists Owner-recorded operating Expenses. */
public final class OwnerExpenseStore {
    private final GymFlowDatabase database;

    /** Creates an Expense store backed by the supplied database. */
    public OwnerExpenseStore(GymFlowDatabase database) {
        this.database = database;
    }

    /** Inserts one Expense when requested by an active Owner. */
    public Expense add(AddExpenseRequest request, String description, long ownerAccountId) {
        String sql = """
                INSERT INTO expenses(expense_date, amount_cents, method, category, description,
                    recorded_by_account_id, created_at)
                SELECT ?, ?, ?, ?, ?, id, ? FROM accounts
                WHERE id = ? AND role = 'OWNER' AND is_active = 1
                """;
        Instant createdAt = Instant.now();
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, request.expenseDate().toString());
            statement.setLong(2, request.amount().movePointRight(2).longValueExact());
            statement.setString(3, request.method().name());
            statement.setString(4, request.category().name());
            statement.setString(5, description.isEmpty() ? null : description);
            statement.setString(6, createdAt.toString());
            statement.setLong(7, ownerAccountId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("An active Owner is required");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated Expense ID returned");
                }
                return new Expense(keys.getLong(1), request.expenseDate(), request.amount(),
                        request.method(), request.category(), description, ownerAccountId, createdAt);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to add Expense", exception);
        }
    }

    /** Lists all Expenses, or only one category when supplied. */
    public List<Expense> list(ExpenseCategory category) {
        String sql = """
                SELECT * FROM expenses
                WHERE ? IS NULL OR category = ?
                ORDER BY expense_date DESC, id DESC
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            String categoryName = category == null ? null : category.name();
            statement.setString(1, categoryName);
            statement.setString(2, categoryName);
            try (ResultSet results = statement.executeQuery()) {
                List<Expense> found = new ArrayList<>();
                while (results.next()) {
                    found.add(read(results));
                }
                return found;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load Expenses", exception);
        }
    }

    /** Totals Expenses within a half-open date range. */
    public BigDecimal total(LocalDate start, LocalDate end) {
        String sql = """
                SELECT COALESCE(SUM(amount_cents), 0) FROM expenses
                WHERE expense_date >= ? AND expense_date < ?
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, start.toString());
            statement.setString(2, end.toString());
            try (ResultSet results = statement.executeQuery()) {
                return BigDecimal.valueOf(results.getLong(1), 2);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to total Expenses", exception);
        }
    }

    private static Expense read(ResultSet results) throws SQLException {
        String description = results.getString("description");
        return new Expense(results.getLong("id"), LocalDate.parse(results.getString("expense_date")),
                BigDecimal.valueOf(results.getLong("amount_cents"), 2),
                PaymentMethod.valueOf(results.getString("method")),
                ExpenseCategory.valueOf(results.getString("category")),
                description == null ? "" : description,
                results.getLong("recorded_by_account_id"),
                Instant.parse(results.getString("created_at")));
    }
}
