package com.gymflow.model;

import java.time.Instant;
import java.util.List;

/** A completed Member Workout and its ordered sets. */
public record Workout(long id, long memberAccountId, Instant performedAt, String notes,
        Instant createdAt, Instant updatedAt, List<WorkoutSet> sets) {
}
