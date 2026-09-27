package com.gymflow.data;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymflow.model.SaveWorkoutRequest;
import com.gymflow.model.Workout;
import com.gymflow.model.WorkoutSet;
import com.gymflow.model.WorkoutSetInput;

/** Supabase-backed Member Workout operations. */
public final class SupabaseWorkoutStore {
    private static final String SET_FIELDS = "id,workout_id,position,exercise_name,repetitions,"
            + "duration_seconds,resistance_grams,created_at,updated_at";
    private static final String FIELDS = "id,member_account_id,started_at,ended_at,notes,"
            + "created_at,updated_at,corrected_at,corrected_by_account_id,correction_reason,"
            + "workout_sets(" + SET_FIELDS + ")";
    private final SupabaseDataClient client;

    /** Creates the store. */
    public SupabaseWorkoutStore(SupabaseDataClient client) {
        this.client = client;
    }

    /** Lists the Member's Workouts. */
    public List<Workout> findByMember(long memberId) {
        JsonNode rows = client.get("workouts?select=" + FIELDS + "&member_account_id=eq."
                + memberId + "&order=started_at.desc");
        return StreamSupport.stream(rows.spliterator(), false).map(this::workout).toList();
    }

    /** Creates a completed or open Workout atomically with its sets. */
    public Workout create(long memberId, SaveWorkoutRequest request, Instant now) {
        return save(null, memberId, request);
    }

    /** Replaces a Workout atomically with its sets. */
    public Workout update(long memberId, long id, SaveWorkoutRequest request, Instant now) {
        return save(id, memberId, request);
    }

    /** Replaces and closes an open Workout atomically. */
    public Workout checkOut(long memberId, long id, SaveWorkoutRequest request, Instant now) {
        return save(id, memberId, request);
    }

    /** Deletes the Member's Workout. */
    public void delete(long memberId, long id) {
        client.rpc("delete_member_workout", Map.of("p_workout_id", id));
    }

    private Workout save(Long id, long memberId, SaveWorkoutRequest request) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("p_workout_id", id);
        arguments.put("p_started_at", request.startedAt().toString());
        arguments.put("p_ended_at", request.endedAt() == null ? null : request.endedAt().toString());
        arguments.put("p_notes", request.notes());
        arguments.put("p_sets", request.sets().stream().map(this::set).toList());
        long workoutId = client.rpc("save_member_workout", arguments).path("id").asLong();
        JsonNode rows = client.get("workouts?select=" + FIELDS + "&id=eq." + workoutId
                + "&member_account_id=eq." + memberId + "&limit=1");
        if (!rows.isArray() || rows.size() != 1) {
            throw new IllegalStateException("Saved Workout could not be loaded");
        }
        return workout(rows.get(0));
    }

    private Map<String, Object> set(WorkoutSetInput input) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("exercise_name", input.exerciseName());
        row.put("repetitions", input.repetitions());
        row.put("duration_seconds", input.durationSeconds());
        row.put("resistance_grams", input.resistanceKilograms() == null ? null
                : input.resistanceKilograms().movePointRight(3)
                        .setScale(0, RoundingMode.UNNECESSARY).longValueExact());
        return row;
    }

    private Workout workout(JsonNode row) {
        List<WorkoutSet> sets = new ArrayList<>();
        row.path("workout_sets").forEach(item -> sets.add(SupabaseRows.workoutSet(item)));
        sets.sort(Comparator.comparingInt(WorkoutSet::position));
        return new Workout(row.path("id").asLong(), row.path("member_account_id").asLong(),
                SupabaseRows.instant(row, "started_at"),
                SupabaseRows.nullableInstant(row, "ended_at"),
                SupabaseRows.nullableText(row, "notes"),
                SupabaseRows.instant(row, "created_at"), SupabaseRows.instant(row, "updated_at"),
                List.copyOf(sets), SupabaseRows.nullableInstant(row, "corrected_at"),
                nullableLong(row, "corrected_by_account_id"),
                SupabaseRows.nullableText(row, "correction_reason"));
    }

    private static Long nullableLong(JsonNode row, String field) {
        JsonNode value = row.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asLong();
    }
}
