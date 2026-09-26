package com.gymflow.data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.gymflow.model.BodyMetric;

/** SQLite persistence for Member-owned body-mass readings. */
public final class BodyMetricStore {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private final GymFlowDatabase database;

    /** Creates a store backed by the supplied database. */
    public BodyMetricStore(GymFlowDatabase database) {
        this.database = database;
    }

    /** Lists an active Member's readings in newest-first order. */
    public List<BodyMetric> findByMember(long memberId) {
        String sql = "SELECT * FROM body_metrics WHERE member_account_id = ? "
                + "ORDER BY measurement_date DESC, id DESC";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            requireActiveMember(connection, memberId);
            statement.setLong(1, memberId);
            try (ResultSet results = statement.executeQuery()) {
                List<BodyMetric> metrics = new ArrayList<>();
                while (results.next()) {
                    metrics.add(metric(results));
                }
                return List.copyOf(metrics);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load body-mass readings", exception);
        }
    }

    /** Stores a new reading for the Member. */
    public BodyMetric create(long memberId, LocalDate date, long grams, Instant now) {
        String sql = "INSERT INTO body_metrics(member_account_id, measurement_date, weight_grams, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            requireActiveMember(connection, memberId);
            statement.setLong(1, memberId);
            statement.setString(2, date.toString());
            statement.setLong(3, grams);
            statement.setString(4, TIMESTAMP.format(now));
            statement.setString(5, TIMESTAMP.format(now));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No body-mass reading ID");
                }
                return find(connection, keys.getLong(1), memberId);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save body-mass reading", exception);
        }
    }

    /** Updates a Member-owned reading. */
    public BodyMetric update(long memberId, long id, LocalDate date, long grams, Instant now) {
        String sql = "UPDATE body_metrics SET measurement_date = ?, weight_grams = ?, updated_at = ? "
                + "WHERE id = ? AND member_account_id = ?";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            requireActiveMember(connection, memberId);
            statement.setString(1, date.toString());
            statement.setLong(2, grams);
            statement.setString(3, TIMESTAMP.format(now));
            statement.setLong(4, id);
            statement.setLong(5, memberId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Body-mass reading not found");
            }
            return find(connection, id, memberId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update body-mass reading", exception);
        }
    }

    /** Deletes a Member-owned reading. */
    public void delete(long memberId, long id) {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM body_metrics WHERE id = ? AND member_account_id = ?")) {
            requireActiveMember(connection, memberId);
            statement.setLong(1, id);
            statement.setLong(2, memberId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Body-mass reading not found");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete body-mass reading", exception);
        }
    }

    private static BodyMetric find(Connection connection, long id, long memberId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM body_metrics WHERE id = ? AND member_account_id = ?")) {
            statement.setLong(1, id);
            statement.setLong(2, memberId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new IllegalArgumentException("Body-mass reading not found");
                }
                return metric(results);
            }
        }
    }

    private static BodyMetric metric(ResultSet results) throws SQLException {
        return new BodyMetric(results.getLong("id"), results.getLong("member_account_id"),
                LocalDate.parse(results.getString("measurement_date")),
                BigDecimal.valueOf(results.getLong("weight_grams"), 3),
                Instant.parse(results.getString("created_at")), Instant.parse(results.getString("updated_at")));
    }

    private static void requireActiveMember(Connection connection, long memberId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM member_profiles p "
                + "JOIN accounts a ON a.id = p.account_id WHERE p.account_id = ? "
                + "AND a.role = 'MEMBER' AND a.is_active = 1")) {
            statement.setLong(1, memberId);
            if (!statement.executeQuery().next()) {
                throw new IllegalArgumentException("An active Member account is required");
            }
        }
    }
}
