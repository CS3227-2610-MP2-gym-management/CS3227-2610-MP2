package com.gymflow.data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.gymflow.model.SaveWorkoutRequest;
import com.gymflow.model.Workout;
import com.gymflow.model.WorkoutSet;
import com.gymflow.model.WorkoutSetInput;

/** SQLite persistence for Member-owned Workouts. */
public final class WorkoutStore {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private final GymFlowDatabase database;

    /** Creates a Workout store backed by the supplied database. */
    public WorkoutStore(GymFlowDatabase database) {
        this.database = database;
    }

    /** Returns an active Member's Workouts in deterministic newest-first order. */
    public List<Workout> findByMember(long memberId) {
        String sql = "SELECT id FROM workouts WHERE member_account_id = ? "
                + "ORDER BY ended_at DESC, id DESC";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            requireActiveMember(connection, memberId);
            statement.setLong(1, memberId);
            try (ResultSet results = statement.executeQuery()) {
                List<Workout> workouts = new ArrayList<>();
                while (results.next()) {
                    workouts.add(find(connection, results.getLong(1)));
                }
                return workouts;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load Workouts", exception);
        }
    }

    /** Creates a Member-owned Workout and its complete set collection. */
    public Workout create(long memberId, SaveWorkoutRequest request, Instant now) {
        return save(memberId, null, request, now);
    }

    /** Replaces a Member-owned Workout and its complete set collection. */
    public Workout update(long memberId, long id, SaveWorkoutRequest request, Instant now) {
        return save(memberId, id, request, now);
    }

    /** Permanently deletes a Member-owned Workout and its cascaded sets. */
    public void delete(long memberId, long id) {
        String sql = "DELETE FROM workouts WHERE id = ? AND member_account_id = ?";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            try {
                requireActiveMember(connection, memberId);
                statement.setLong(1, id);
                statement.setLong(2, memberId);
                if (statement.executeUpdate() != 1) {
                    throw new IllegalArgumentException("Workout not found");
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete Workout", exception);
        }
    }

    private Workout save(long memberId, Long id, SaveWorkoutRequest request, Instant now) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                requireActiveMember(connection, memberId);
                long workoutId = id == null ? insertWorkout(connection, memberId, request, now)
                        : replaceWorkout(connection, memberId, id, request, now);
                insertSets(connection, workoutId, request.sets(), now);
                Workout saved = find(connection, workoutId);
                connection.commit();
                return saved;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save Workout", exception);
        }
    }

    private static long insertWorkout(Connection connection, long memberId,
            SaveWorkoutRequest request, Instant now) throws SQLException {
        String sql = "INSERT INTO workouts(member_account_id, started_at, ended_at, notes, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, memberId);
            bindWorkout(statement, request, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No Workout ID");
                }
                return keys.getLong(1);
            }
        }
    }

    private static long replaceWorkout(Connection connection, long memberId, long id,
            SaveWorkoutRequest request, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE workouts SET started_at = ?, ended_at = ?, notes = ?, updated_at = ? "
                        + "WHERE id = ? AND member_account_id = ?")) {
            statement.setString(1, TIMESTAMP.format(request.startedAt()));
            statement.setString(2, TIMESTAMP.format(request.endedAt()));
            statement.setString(3, request.notes());
            statement.setString(4, TIMESTAMP.format(now));
            statement.setLong(5, id);
            statement.setLong(6, memberId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Workout not found");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM workout_sets WHERE workout_id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
        return id;
    }

    private static void bindWorkout(PreparedStatement statement, SaveWorkoutRequest request,
            Instant now) throws SQLException {
        String timestamp = TIMESTAMP.format(now);
        statement.setString(2, TIMESTAMP.format(request.startedAt()));
        statement.setString(3, TIMESTAMP.format(request.endedAt()));
        statement.setString(4, request.notes());
        statement.setString(5, timestamp);
        statement.setString(6, timestamp);
    }

    private static void insertSets(Connection connection, long workoutId,
            List<WorkoutSetInput> sets, Instant now) throws SQLException {
        String sql = "INSERT INTO workout_sets(workout_id, position, exercise_name, repetitions, "
                + "duration_seconds, resistance_grams, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int position = 0; position < sets.size(); position++) {
                WorkoutSetInput set = sets.get(position);
                statement.setLong(1, workoutId);
                statement.setInt(2, position);
                statement.setString(3, set.exerciseName());
                nullableInteger(statement, 4, set.repetitions());
                nullableInteger(statement, 5, set.durationSeconds());
                if (set.resistanceKilograms() == null) {
                    statement.setNull(6, java.sql.Types.INTEGER);
                } else {
                    statement.setLong(6, set.resistanceKilograms().movePointRight(3).longValueExact());
                }
                statement.setString(7, TIMESTAMP.format(now));
                statement.setString(8, TIMESTAMP.format(now));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void nullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static Workout find(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM workouts WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new SQLException("Workout not found");
                }
                return new Workout(id, results.getLong("member_account_id"),
                        Instant.parse(results.getString("started_at")),
                        Instant.parse(results.getString("ended_at")), results.getString("notes"),
                        Instant.parse(results.getString("created_at")),
                        Instant.parse(results.getString("updated_at")), loadSets(connection, id));
            }
        }
    }

    private static List<WorkoutSet> loadSets(Connection connection, long workoutId) throws SQLException {
        List<WorkoutSet> sets = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM workout_sets WHERE workout_id = ? ORDER BY position")) {
            statement.setLong(1, workoutId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    long resistanceGrams = results.getLong("resistance_grams");
                    Long grams = results.wasNull() ? null : resistanceGrams;
                    sets.add(new WorkoutSet(results.getLong("id"), workoutId,
                            results.getInt("position"), results.getString("exercise_name"),
                            nullableInteger(results, "repetitions"),
                            nullableInteger(results, "duration_seconds"),
                            grams == null ? null : BigDecimal.valueOf(grams, 3),
                            Instant.parse(results.getString("created_at")),
                            Instant.parse(results.getString("updated_at"))));
                }
            }
        }
        return List.copyOf(sets);
    }

    private static Integer nullableInteger(ResultSet results, String column) throws SQLException {
        int value = results.getInt(column);
        return results.wasNull() ? null : value;
    }

    private static void requireActiveMember(Connection connection, long memberId) throws SQLException {
        String sql = "SELECT 1 FROM member_profiles p JOIN accounts a ON a.id = p.account_id "
                + "WHERE p.account_id = ? AND a.role = 'MEMBER' AND a.is_active = 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberId);
            if (!statement.executeQuery().next()) {
                throw new IllegalArgumentException("An active Member account is required");
            }
        }
    }
}
