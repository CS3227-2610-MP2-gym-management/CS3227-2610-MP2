package com.gymflow.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GymFlowDatabaseTest {
    @TempDir
    Path directory;

    @Test
    void migratesLegacyTimestampsWithoutLosingDataAndIsIdempotent() throws Exception {
        Path file = directory.resolve("gymflow.db");
        createLegacyDatabase(file);
        GymFlowDatabase database = new GymFlowDatabase(file);

        database.initialize();
        database.initialize();

        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            assertEquals(1, value(statement, "SELECT COUNT(*) FROM accounts"));
            assertEquals("2026-01-01T00:00:00Z",
                    text(statement, "SELECT updated_at FROM accounts WHERE id = 1"));
            assertNotNull(text(statement, "SELECT created_at FROM memberships WHERE id = 1"));
            assertNotNull(text(statement, "SELECT updated_at FROM memberships WHERE id = 1"));
            assertNotNull(text(statement, "SELECT created_at FROM payments WHERE id = 1"));
            assertEquals(5, value(statement, "PRAGMA user_version"));
            assertEquals(1, value(statement,
                    "SELECT COUNT(*) FROM sqlite_schema WHERE type = 'table' AND name = 'visits'"));
            assertEquals(1, value(statement,
                    "SELECT COUNT(*) FROM sqlite_schema WHERE type = 'table' AND name = 'expenses'"));
            assertEquals(1, value(statement,
                    "SELECT COUNT(*) FROM sqlite_schema WHERE type = 'table' AND name = 'announcements'"));
        }
    }

    @Test
    void visitSchemaRejectsMultipleOpenVisitsAndInvalidExitOrder() throws Exception {
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        database.initialize();
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            insertMember(statement);
            statement.executeUpdate("""
                    INSERT INTO visits(member_account_id, entered_at, exited_at, created_at)
                    VALUES (1, '2026-09-15T01:00:00.000Z', NULL, '2026-09-15T01:00:00.000Z')
                    """);

            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO visits(member_account_id, entered_at, exited_at, created_at)
                    VALUES (1, '2026-09-15T02:00:00.000Z', NULL, '2026-09-15T02:00:00.000Z')
                    """));
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO visits(member_account_id, entered_at, exited_at, created_at)
                    VALUES (1, '2026-09-15T03:00:00.000Z', '2026-09-15T02:00:00.000Z',
                        '2026-09-15T03:00:00.000Z')
                    """));
            statement.executeUpdate("""
                    INSERT INTO visits(member_account_id, entered_at, exited_at, created_at)
                    VALUES (1, '2026-09-15T04:00:00.000Z', '2026-09-15T04:00:00.500Z',
                        '2026-09-15T04:00:00.000Z')
                    """);
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO visits(member_account_id, entered_at, exited_at, created_at)
                    VALUES (1, '2026-09-15T05:00:00.500Z', '2026-09-15T05:00:00.000Z',
                        '2026-09-15T05:00:00.500Z')
                    """));
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO visits(member_account_id, entered_at, exited_at, created_at)
                    VALUES (1, '2026-09-15T06:00:00.500002Z', '2026-09-15T06:00:00.500001Z',
                        '2026-09-15T06:00:00.500002Z')
                    """));
        }
    }

    @Test
    void resetRemovesVisitsAndExpensesAndRecreatesLatestSchema() throws Exception {
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        database.initialize();
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            insertMember(statement);
            statement.executeUpdate("""
                    INSERT INTO visits(member_account_id, entered_at, exited_at, created_at)
                    VALUES (1, '2026-09-15T01:00:00.000Z', '2026-09-15T02:00:00.000Z',
                        '2026-09-15T01:00:00.000Z')
                    """);
            statement.executeUpdate("""
                    INSERT INTO expenses(expense_date, amount_cents, method, category, description,
                        recorded_by_account_id, created_at)
                    VALUES ('2026-09-15', 5000, 'CARD', 'OTHER', NULL, 1,
                        '2026-09-15T01:00:00Z')
                    """);
            statement.executeUpdate("""
                    INSERT INTO announcements(title, content, published_at, created_by_account_id,
                        created_at, updated_at)
                    VALUES ('Notice', 'Content', '2026-09-15T01:00:00Z', 1,
                        '2026-09-15T01:00:00Z', '2026-09-15T01:00:00Z')
                    """);
        }

        database.reset();

        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            assertEquals(0, value(statement, "SELECT COUNT(*) FROM visits"));
            assertEquals(0, value(statement, "SELECT COUNT(*) FROM expenses"));
            assertEquals(0, value(statement, "SELECT COUNT(*) FROM announcements"));
            assertEquals(5, value(statement, "PRAGMA user_version"));
        }
    }

    @Test
    void migratesVersionTwoVisitsWithEmptyCorrectionMetadata() throws Exception {
        Path file = directory.resolve("version-two.db");
        createVersionTwoDatabase(file);
        GymFlowDatabase database = new GymFlowDatabase(file);

        database.initialize();
        database.initialize();

        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            assertEquals(5, value(statement, "PRAGMA user_version"));
            assertEquals(1, value(statement, "SELECT COUNT(*) FROM visits"));
            assertEquals(null, text(statement, "SELECT corrected_at FROM visits WHERE id = 1"));
            assertEquals(null, text(statement, "SELECT corrected_by_account_id FROM visits WHERE id = 1"));
            assertEquals(null, text(statement, "SELECT correction_reason FROM visits WHERE id = 1"));
        }
    }

    @Test
    void failedSchemaRecreationRollsBackDroppedTables() throws Exception {
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        database.initialize();
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO accounts(id, email, password_hash, password_salt, password_iterations,
                        role, is_active, created_at, updated_at)
                    VALUES (1, 'owner@example.com', 'hash', 'salt', 1, 'OWNER', 1,
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                    """);
        }

        assertThrows(IllegalStateException.class,
                () -> database.recreateSchema(List.of("CREATE TABLE broken (")));

        try (Connection connection = database.connect();
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM accounts")) {
            assertEquals(1, results.getInt(1));
        }
    }

    private static void createLegacyDatabase(Path file) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE accounts (id INTEGER PRIMARY KEY, email TEXT, password_hash TEXT,
                        password_salt TEXT, password_iterations INTEGER, role TEXT, is_active INTEGER,
                        created_at TEXT)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE member_profiles (account_id INTEGER PRIMARY KEY, member_number TEXT,
                        full_name TEXT, phone_number TEXT, date_of_birth TEXT)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE memberships (id INTEGER PRIMARY KEY, member_account_id INTEGER,
                        start_date TEXT, expiry_date TEXT, is_active INTEGER)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE payments (id INTEGER PRIMARY KEY, membership_id INTEGER, amount_cents INTEGER,
                        method TEXT, paid_at TEXT, reference TEXT, recorded_by_account_id INTEGER)
                    """);
            statement.executeUpdate("INSERT INTO accounts VALUES "
                    + "(1, 'member@example.com', 'hash', 'salt', 1, 'MEMBER', 1, '2026-01-01T00:00:00Z')");
            statement.executeUpdate("INSERT INTO member_profiles VALUES "
                    + "(1, 'M000001', 'Member', '+65 8123 4567', NULL)");
            statement.executeUpdate("INSERT INTO memberships VALUES (1, 1, '2026-01-01', '2026-01-31', 1)");
            statement.executeUpdate("INSERT INTO payments VALUES "
                    + "(1, 1, 5000, 'CARD', '2026-01-01T00:00:00Z', NULL, 1)");
        }
    }

    private static void createVersionTwoDatabase(Path file) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE accounts (id INTEGER PRIMARY KEY, email TEXT, password_hash TEXT,
                        password_salt TEXT, password_iterations INTEGER, role TEXT, is_active INTEGER,
                        created_at TEXT, updated_at TEXT)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE member_profiles (account_id INTEGER PRIMARY KEY, member_number TEXT,
                        full_name TEXT, phone_number TEXT, date_of_birth TEXT)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE visits (id INTEGER PRIMARY KEY, member_account_id INTEGER,
                        entered_at TEXT, exited_at TEXT, created_at TEXT)
                    """);
            statement.executeUpdate("INSERT INTO accounts VALUES "
                    + "(1, 'member@example.com', 'hash', 'salt', 1, 'MEMBER', 1, "
                    + "'2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");
            statement.executeUpdate("INSERT INTO member_profiles VALUES "
                    + "(1, 'M000001', 'Member', '+65 8123 4567', NULL)");
            statement.executeUpdate("INSERT INTO visits VALUES "
                    + "(1, 1, '2026-09-15T01:00:00.000Z', '2026-09-15T02:00:00.000Z', "
                    + "'2026-09-15T01:00:00.000Z')");
            statement.execute("PRAGMA user_version = 2");
        }
    }

    private static void insertMember(Statement statement) throws Exception {
        statement.executeUpdate("""
                INSERT INTO accounts(id, email, password_hash, password_salt, password_iterations,
                    role, is_active, created_at, updated_at)
                VALUES (1, 'member@example.com', 'hash', 'salt', 1, 'MEMBER', 1,
                    '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                """);
        statement.executeUpdate("""
                INSERT INTO member_profiles(account_id, member_number, full_name, phone_number)
                VALUES (1, 'M000001', 'Member', '+65 8123 4567')
                """);
    }

    private static int value(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            return result.getInt(1);
        }
    }

    private static String text(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            return result.getString(1);
        }
    }
}
