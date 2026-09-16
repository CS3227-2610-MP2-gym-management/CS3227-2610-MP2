package com.gymflow.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Owns the SQLite file and centralized application schema. */
public final class GymFlowDatabase {
    private static final int SCHEMA_VERSION = 5;
    private static final String VISITS_TABLE = """
        CREATE TABLE visits (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            member_account_id INTEGER NOT NULL REFERENCES member_profiles(account_id) ON DELETE CASCADE,
            entered_at TEXT NOT NULL,
            exited_at TEXT,
            created_at TEXT NOT NULL,
            corrected_at TEXT,
            corrected_by_account_id INTEGER REFERENCES accounts(id),
            correction_reason TEXT,
            CHECK (length(entered_at) = 24
                AND entered_at GLOB '????-??-??T??:??:??.???Z'
                AND unixepoch(entered_at, 'subsec') IS NOT NULL),
            CHECK (length(created_at) = 24
                AND created_at GLOB '????-??-??T??:??:??.???Z'
                AND unixepoch(created_at, 'subsec') IS NOT NULL),
            CHECK (exited_at IS NULL OR (
                length(exited_at) = 24
                AND exited_at GLOB '????-??-??T??:??:??.???Z'
                AND unixepoch(exited_at, 'subsec') IS NOT NULL
                AND exited_at >= entered_at
            )),
            CHECK (corrected_at IS NULL OR (
                length(corrected_at) = 24
                AND corrected_at GLOB '????-??-??T??:??:??.???Z'
                AND unixepoch(corrected_at, 'subsec') IS NOT NULL
            )),
            CHECK ((corrected_at IS NULL AND corrected_by_account_id IS NULL
                    AND correction_reason IS NULL)
                OR (corrected_at IS NOT NULL AND corrected_by_account_id IS NOT NULL
                    AND length(trim(correction_reason)) > 0))
        )
        """;
    private static final String VISITS_INDEX = """
        CREATE UNIQUE INDEX one_open_visit_per_member
        ON visits(member_account_id) WHERE exited_at IS NULL
        """;
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
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
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
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
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
            recorded_by_account_id INTEGER NOT NULL REFERENCES accounts(id),
            created_at TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE expenses (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            expense_date TEXT NOT NULL,
            amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
            method TEXT NOT NULL CHECK (method IN ('CASH', 'CARD', 'TRANSFER')),
            category TEXT NOT NULL CHECK (category IN (
                'MAINTENANCE', 'UTILITIES', 'EQUIPMENT', 'SUPPLIES', 'RENT', 'OTHER')),
            description TEXT,
            recorded_by_account_id INTEGER NOT NULL REFERENCES accounts(id),
            created_at TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE announcements (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL CHECK (length(trim(title)) > 0),
            content TEXT NOT NULL CHECK (length(trim(content)) > 0),
            published_at TEXT NOT NULL,
            created_by_account_id INTEGER NOT NULL REFERENCES accounts(id),
            withdrawn_at TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            CHECK (withdrawn_at IS NULL OR withdrawn_at >= published_at)
        )
        """,
        VISITS_TABLE,
        VISITS_INDEX
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
            try (Connection connection = connect()) {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    for (String sql : SCHEMA) {
                        statement.executeUpdate(ifMissing(sql));
                    }
                    migrateLegacyTimestamps(connection, statement);
                    migrateVisits(connection, statement);
                    statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
                    connection.commit();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
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
                    statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
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

    private static void migrateLegacyTimestamps(Connection connection,
            Statement statement) throws SQLException {
        String migratedAt = Instant.now().toString();
        if (!hasColumn(connection, "accounts", "updated_at")) {
            statement.executeUpdate("ALTER TABLE accounts ADD COLUMN updated_at TEXT NOT NULL DEFAULT ''");
            statement.executeUpdate("UPDATE accounts SET updated_at = created_at");
        }
        if (!hasColumn(connection, "memberships", "created_at")) {
            statement.executeUpdate("ALTER TABLE memberships ADD COLUMN created_at TEXT NOT NULL DEFAULT ''");
            statement.executeUpdate("UPDATE memberships SET created_at = '" + migratedAt + "'");
        }
        if (!hasColumn(connection, "memberships", "updated_at")) {
            statement.executeUpdate("ALTER TABLE memberships ADD COLUMN updated_at TEXT NOT NULL DEFAULT ''");
            statement.executeUpdate("UPDATE memberships SET updated_at = created_at");
        }
        if (!hasColumn(connection, "payments", "created_at")) {
            statement.executeUpdate("ALTER TABLE payments ADD COLUMN created_at TEXT NOT NULL DEFAULT ''");
            statement.executeUpdate("UPDATE payments SET created_at = '" + migratedAt + "'");
        }
    }

    private static void migrateVisits(Connection connection, Statement statement) throws SQLException {
        if (hasColumn(connection, "visits", "corrected_at")) {
            return;
        }
        statement.executeUpdate("DROP INDEX IF EXISTS one_open_visit_per_member");
        statement.executeUpdate("ALTER TABLE visits RENAME TO visits_version_two");
        statement.executeUpdate(VISITS_TABLE);
        statement.executeUpdate("""
                INSERT INTO visits(id, member_account_id, entered_at, exited_at, created_at)
                SELECT id, member_account_id, entered_at, exited_at, created_at FROM visits_version_two
                """);
        statement.executeUpdate("DROP TABLE visits_version_two");
        statement.executeUpdate(VISITS_INDEX);
    }

    private static boolean hasColumn(Connection connection, String table,
            String column) throws SQLException {
        try (Statement statement = connection.createStatement();
                var results = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (results.next()) {
                if (column.equals(results.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
