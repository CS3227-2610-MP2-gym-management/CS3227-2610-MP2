package com.gymflow.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.gymflow.model.MemberVisitState;
import com.gymflow.model.Visit;

/** Performs authorized transactional Visit changes for Members. */
public final class MemberVisitStore {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private final GymFlowDatabase database;

    /** Creates a Member Visit store backed by the supplied database. */
    public MemberVisitStore(GymFlowDatabase database) {
        this.database = database;
    }

    /** Returns the current state derived from the Member's open Visit. */
    public MemberVisitState currentState(long memberId) {
        try (Connection connection = database.connect()) {
            requireActiveMember(connection, memberId);
            return new MemberVisitState(findOpenVisit(connection, memberId));
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load Visit state", exception);
        }
    }

    /** Creates an open Visit after validating the Member and Membership in one transaction. */
    public Visit checkIn(long memberId, LocalDate date, Instant now) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                requireActiveMember(connection, memberId);
                if (!hasValidMembership(connection, memberId, date)) {
                    throw new IllegalArgumentException("A valid Membership is required to check in");
                }
                if (findOpenVisit(connection, memberId) != null) {
                    throw new IllegalArgumentException("You are already checked in");
                }
                Visit visit = insertOpenVisit(connection, memberId, now);
                connection.commit();
                return visit;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                if (exception instanceof SQLException sql && sql.getErrorCode() == 19) {
                    throw new IllegalArgumentException("You are already checked in", sql);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to check in", exception);
        }
    }

    /** Closes the Member's open Visit without rechecking Membership validity. */
    public Visit checkOut(long memberId, Instant now) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                requireActiveMember(connection, memberId);
                Visit open = findOpenVisit(connection, memberId);
                if (open == null) {
                    throw new IllegalArgumentException("You are not currently checked in");
                }
                if (now.isBefore(open.enteredAt().plusSeconds(60))) {
                    throw new IllegalArgumentException(
                            "Wait at least one minute after check-in before checking out");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE workouts SET ended_at = ?, updated_at = ? WHERE id = ? AND ended_at IS NULL")) {
                    statement.setString(1, TIMESTAMP.format(now));
                    statement.setString(2, TIMESTAMP.format(now));
                    statement.setLong(3, open.id());
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException("You are not currently checked in");
                    }
                }
                Visit closed = findById(connection, open.id());
                connection.commit();
                return closed;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to check out", exception);
        }
    }

    private static void requireActiveMember(Connection connection, long memberId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM member_profiles p JOIN accounts a ON a.id = p.account_id
                WHERE p.account_id = ? AND a.role = 'MEMBER' AND a.is_active = 1
                """)) {
            statement.setLong(1, memberId);
            if (!statement.executeQuery().next()) {
                throw new IllegalArgumentException("An active Member account is required");
            }
        }
    }

    private static boolean hasValidMembership(Connection connection, long memberId, LocalDate date)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM memberships WHERE member_account_id = ? AND is_active = 1
                  AND start_date <= ? AND expiry_date >= ? LIMIT 1
                """)) {
            statement.setLong(1, memberId);
            statement.setString(2, date.toString());
            statement.setString(3, date.toString());
            return statement.executeQuery().next();
        }
    }

    private static Visit insertOpenVisit(Connection connection, long memberId, Instant now) throws SQLException {
        String timestamp = TIMESTAMP.format(now);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workouts(member_account_id, started_at, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, memberId);
            statement.setString(2, timestamp);
            statement.setString(3, timestamp);
            statement.setString(4, timestamp);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated Visit ID returned");
                }
                return findById(connection, keys.getLong(1));
            }
        }
    }

    private static Visit findOpenVisit(Connection connection, long memberId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM workouts WHERE member_account_id = ? AND ended_at IS NULL")) {
            statement.setLong(1, memberId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readVisit(results) : null;
            }
        }
    }

    private static Visit findById(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM workouts WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new SQLException("Visit not found after update");
                }
                return readVisit(results);
            }
        }
    }

    private static Visit readVisit(ResultSet results) throws SQLException {
        String exitedAt = results.getString("ended_at");
        String correctedAt = results.getString("corrected_at");
        long correctedBy = results.getLong("corrected_by_account_id");
        Long correctedById = results.wasNull() ? null : correctedBy;
        return new Visit(results.getLong("id"), results.getLong("member_account_id"),
                Instant.parse(results.getString("started_at")), exitedAt == null ? null : Instant.parse(exitedAt),
                Instant.parse(results.getString("created_at")),
                correctedAt == null ? null : Instant.parse(correctedAt), correctedById,
                results.getString("correction_reason"));
    }
}
