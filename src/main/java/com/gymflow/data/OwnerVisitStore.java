package com.gymflow.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.gymflow.model.Visit;
import com.gymflow.model.VisitOverview;

/** Reads Visit records for Owner attendance oversight. */
public final class OwnerVisitStore {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private final GymFlowDatabase database;

    /** Creates a Visit store backed by the supplied database. */
    public OwnerVisitStore(GymFlowDatabase database) {
        this.database = database;
    }

    /** Finds Visits matching a Member name or email. */
    public List<VisitOverview> search(String query, boolean currentlyVisitingOnly) {
        String pattern = "%" + escape(query.toLowerCase(Locale.ROOT)) + "%";
        String sql = """
                SELECT v.*, p.member_number, p.full_name, a.email
                FROM visits v
                JOIN member_profiles p ON p.account_id = v.member_account_id
                JOIN accounts a ON a.id = p.account_id
                WHERE (lower(p.full_name) LIKE ? ESCAPE '\\'
                    OR lower(a.email) LIKE ? ESCAPE '\\')
                  AND (? = 0 OR v.exited_at IS NULL)
                ORDER BY v.entered_at DESC, v.id DESC
                """;
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            statement.setBoolean(3, currentlyVisitingOnly);
            try (ResultSet results = statement.executeQuery()) {
                List<VisitOverview> found = new ArrayList<>();
                while (results.next()) {
                    found.add(new VisitOverview(readVisit(results),
                            results.getString("member_number"), results.getString("full_name"),
                            results.getString("email")));
                }
                return found;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to search Visits", exception);
        }
    }

    /** Lists one Member's Visits, newest first. */
    public List<Visit> history(long memberId) {
        String sql = """
                SELECT * FROM visits WHERE member_account_id = ?
                ORDER BY entered_at DESC, id DESC
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

    /** Counts Members whose Visit has no exit time. */
    public long countCurrentlyVisiting() {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM visits WHERE exited_at IS NULL");
                ResultSet results = statement.executeQuery()) {
            return results.getLong(1);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to count current Visits", exception);
        }
    }

    /** Corrects a Visit and records the latest Owner-provided reason. */
    public Visit correct(long visitId, Instant enteredAt, Instant exitedAt,
            String reason, long ownerAccountId) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                requireOwner(connection, ownerAccountId);
                Visit current = find(connection, visitId);
                if (current.enteredAt().equals(enteredAt)
                        && java.util.Objects.equals(current.exitedAt(), exitedAt)) {
                    throw new IllegalArgumentException("Change at least one Visit time");
                }
                Instant correctedAt = Instant.now();
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE visits SET entered_at = ?, exited_at = ?, corrected_at = ?,
                            corrected_by_account_id = ?, correction_reason = ? WHERE id = ?
                        """)) {
                    statement.setString(1, TIMESTAMP.format(enteredAt));
                    statement.setString(2, exitedAt == null ? null : TIMESTAMP.format(exitedAt));
                    statement.setString(3, TIMESTAMP.format(correctedAt));
                    statement.setLong(4, ownerAccountId);
                    statement.setString(5, reason);
                    statement.setLong(6, visitId);
                    try {
                        statement.executeUpdate();
                    } catch (SQLException exception) {
                        if (exception.getErrorCode() == 19) {
                            throw new IllegalArgumentException(
                                    "Correction conflicts with another open Visit", exception);
                        }
                        throw exception;
                    }
                }
                Visit corrected = find(connection, visitId);
                connection.commit();
                return corrected;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to correct Visit", exception);
        }
    }

    private static void requireOwner(Connection connection, long ownerAccountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM accounts WHERE id = ? AND role = 'OWNER' AND is_active = 1")) {
            statement.setLong(1, ownerAccountId);
            if (!statement.executeQuery().next()) {
                throw new IllegalArgumentException("An active Owner is required");
            }
        }
    }

    private static Visit find(Connection connection, long visitId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM visits WHERE id = ?")) {
            statement.setLong(1, visitId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new IllegalArgumentException("Visit not found");
                }
                return readVisit(results);
            }
        }
    }

    private static Visit readVisit(ResultSet results) throws SQLException {
        String exitedAt = results.getString("exited_at");
        String correctedAt = results.getString("corrected_at");
        long correctedBy = results.getLong("corrected_by_account_id");
        Long correctedByUserId = results.wasNull() ? null : correctedBy;
        return new Visit(results.getLong("id"), results.getLong("member_account_id"),
                Instant.parse(results.getString("entered_at")),
                exitedAt == null ? null : Instant.parse(exitedAt),
                Instant.parse(results.getString("created_at")),
                correctedAt == null ? null : Instant.parse(correctedAt),
                correctedByUserId, results.getString("correction_reason"));
    }

    private static String escape(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
