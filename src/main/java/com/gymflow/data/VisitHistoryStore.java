package com.gymflow.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.gymflow.model.Visit;

/** Reads a single Member's Visit history. */
public final class VisitHistoryStore {
    private final GymFlowDatabase database;

    /** Creates a Visit history store backed by the supplied database. */
    public VisitHistoryStore(GymFlowDatabase database) {
        this.database = database;
    }

    /** Lists one Member's Visits in deterministic newest-first order. */
    public List<Visit> history(long memberId) {
        String sql = """
                SELECT * FROM workouts WHERE member_account_id = ?
                ORDER BY ended_at IS NULL DESC, ended_at DESC, id DESC
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberId);
            try (ResultSet results = statement.executeQuery()) {
                List<Visit> found = new ArrayList<>();
                while (results.next()) {
                    found.add(readVisit(results));
                }
                return found;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load Member Visits", exception);
        }
    }

    private static Visit readVisit(ResultSet results) throws SQLException {
        String exitedAt = results.getString("ended_at");
        String correctedAt = results.getString("corrected_at");
        long correctedBy = results.getLong("corrected_by_account_id");
        Long correctedByUserId = results.wasNull() ? null : correctedBy;
        return new Visit(results.getLong("id"), results.getLong("member_account_id"),
                Instant.parse(results.getString("started_at")),
                exitedAt == null ? null : Instant.parse(exitedAt),
                Instant.parse(results.getString("created_at")),
                correctedAt == null ? null : Instant.parse(correctedAt),
                correctedByUserId, results.getString("correction_reason"));
    }
}
