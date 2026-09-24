package com.gymflow.model;

import java.math.BigDecimal;
import java.time.Instant;

/** One persisted, ordered exercise set belonging to a Workout. */
public record WorkoutSet(long id, long workoutId, int position, String exerciseName, Integer repetitions,
        Integer durationSeconds, BigDecimal resistanceKilograms, Instant createdAt, Instant updatedAt) {
}
