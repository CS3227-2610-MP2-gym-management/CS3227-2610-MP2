package com.gymflow.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import com.gymflow.auth.PasswordHash;
import com.gymflow.model.Account;
import com.gymflow.model.Role;

/** Persists accounts in SQLite. */
public final class AccountStore {
    private final GymFlowDatabase database;

    /** Creates an account store backed by the supplied database. */
    public AccountStore(GymFlowDatabase database) {
        this.database = database;
    }

    /** Stores an active account and returns its generated identity. */
    public Account create(String email, PasswordHash password, Role role) {
        String normalizedEmail = normalize(email);
        Instant createdAt = Instant.now();
        String sql = """
                INSERT INTO accounts(email, password_hash, password_salt, password_iterations,
                    role, is_active, created_at) VALUES (?, ?, ?, ?, ?, 1, ?)
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, normalizedEmail);
            statement.setString(2, password.hash());
            statement.setString(3, password.salt());
            statement.setInt(4, password.iterations());
            statement.setString(5, role.name());
            statement.setString(6, createdAt.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("Unable to create account");
                }
                return new Account(keys.getLong(1), normalizedEmail, role, true, createdAt);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create account", exception);
        }
    }

    /** Finds an account by email without regard to email case. */
    public Optional<StoredAccount> findByEmail(String email) {
        String sql = "SELECT * FROM accounts WHERE email = ? COLLATE NOCASE";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(email));
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(read(results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read account", exception);
        }
    }

    /** Finds the installation's Owner account. */
    public Optional<StoredAccount> findOwner() {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM accounts WHERE role = 'OWNER'");
                ResultSet results = statement.executeQuery()) {
            return results.next() ? Optional.of(read(results)) : Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read Owner account", exception);
        }
    }

    private static StoredAccount read(ResultSet results) throws SQLException {
        Account account = new Account(
                results.getLong("id"),
                results.getString("email"),
                Role.valueOf(results.getString("role")),
                results.getBoolean("is_active"),
                Instant.parse(results.getString("created_at")));
        PasswordHash password = new PasswordHash(
                results.getString("password_hash"),
                results.getString("password_salt"),
                results.getInt("password_iterations"));
        return new StoredAccount(account, password);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
