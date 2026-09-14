package com.gymflow.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Owns the SQLite file and centralized application schema. */
public final class GymFlowDatabase {
    private static final String[] SCHEMA = {
        """
        CREATE TABLE accounts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            email TEXT NOT NULL COLLATE NOCASE UNIQUE,
            password_hash TEXT NOT NULL,
            password_salt TEXT NOT NULL,
            password_iterations INTEGER NOT NULL,
            role TEXT NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
            is_active INTEGER NOT NULL CHECK (is_active IN (0, 1)),
            created_at TEXT NOT NULL
        )
        """,
        "CREATE UNIQUE INDEX one_owner ON accounts(role) WHERE role = 'OWNER'",
        """
        CREATE TABLE member_profiles (
            account_id INTEGER PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
            member_number TEXT NOT NULL UNIQUE,
            full_name TEXT NOT NULL,
            phone_number TEXT NOT NULL,
            date_of_birth TEXT
        )
        """,
        """
        CREATE TABLE memberships (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            member_account_id INTEGER NOT NULL REFERENCES member_profiles(account_id) ON DELETE CASCADE,
            start_date TEXT NOT NULL,
            expiry_date TEXT NOT NULL,
            is_active INTEGER NOT NULL CHECK (is_active IN (0, 1)),
            CHECK (expiry_date >= start_date)
        )
        """,
        """
        CREATE TABLE payments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            membership_id INTEGER NOT NULL UNIQUE REFERENCES memberships(id) ON DELETE CASCADE,
            amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
            method TEXT NOT NULL CHECK (method IN ('CASH', 'CARD', 'TRANSFER')),
            paid_at TEXT NOT NULL,
            reference TEXT,
            recorded_by_account_id INTEGER NOT NULL REFERENCES accounts(id)
        )
        """
    };

    private final Path file;

    /** Creates a database backed by the supplied file. */
    public GymFlowDatabase(Path file) {
        this.file = file.toAbsolutePath();
    }

    /** Creates missing directories and initializes the schema. */
    public void initialize() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = connect(); Statement statement = connection.createStatement()) {
                for (String sql : SCHEMA) {
                    statement.executeUpdate(ifMissing(sql));
                }
            }
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("Unable to initialize GymFlow data", exception);
        }
    }

    /** Atomically removes all database-backed data and restores an empty schema. */
    public void reset() {
        recreateSchema(Arrays.asList(SCHEMA));
    }

    void recreateSchema(List<String> schema) {
        try (Connection connection = connect()) {
            connection.createStatement().execute("PRAGMA foreign_keys = OFF");
            connection.setAutoCommit(false);
            try {
                List<String> tables = new ArrayList<>();
                try (Statement statement = connection.createStatement();
                        var results = statement.executeQuery(
                                "SELECT name FROM sqlite_schema WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")) {
                    while (results.next()) {
                        tables.add(results.getString(1));
                    }
                }
                try (Statement statement = connection.createStatement()) {
                    for (String table : tables) {
                        statement.executeUpdate("DROP TABLE \"" + table.replace("\"", "\"\"") + "\"");
                    }
                    for (String sql : schema) {
                        statement.executeUpdate(sql);
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to reset GymFlow data", exception);
        }
    }

    Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private static String ifMissing(String sql) {
        return sql.replace("CREATE TABLE", "CREATE TABLE IF NOT EXISTS")
                .replace("CREATE UNIQUE INDEX", "CREATE UNIQUE INDEX IF NOT EXISTS");
    }
}
