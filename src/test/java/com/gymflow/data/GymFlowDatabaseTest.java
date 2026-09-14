package com.gymflow.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GymFlowDatabaseTest {
    @TempDir
    Path directory;

    @Test
    void failedSchemaRecreationRollsBackDroppedTables() throws Exception {
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        database.initialize();
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO accounts VALUES "
                    + "(1, 'owner@example.com', 'hash', 'salt', 1, 'OWNER', 1, '2026-01-01T00:00:00Z')");
        }

        assertThrows(IllegalStateException.class,
                () -> database.recreateSchema(List.of("CREATE TABLE broken (")));

        try (Connection connection = database.connect();
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM accounts")) {
            assertEquals(1, results.getInt(1));
        }
    }
}
