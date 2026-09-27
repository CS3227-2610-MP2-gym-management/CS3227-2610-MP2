package com.gymflow.model;

import java.time.Instant;
import java.util.List;

/** A Member Workout, which may be open, and its ordered sets. */
public record Workout(long id, long memberAccountId, Instant startedAt, Instant endedAt, String notes,
        Instant createdAt, Instant updatedAt, List<WorkoutSet> sets, Instant correctedAt,
        Long correctedByAccountId, String correctionReason) {
    /**
     * Creates a Workout without correction metadata.
     *
     * @param id Workout identifier
     * @param memberAccountId owning Member identifier
     * @param startedAt recorded check-in time
     * @param endedAt recorded check-out time, or {@code null} while open
     * @param notes optional Member notes
     * @param createdAt creation time
     * @param updatedAt most recent update time
     * @param sets ordered immutable exercise sets
     */
    public Workout(long id, long memberAccountId, Instant startedAt, Instant endedAt, String notes,
            Instant createdAt, Instant updatedAt, List<WorkoutSet> sets) {
        this(id, memberAccountId, startedAt, endedAt, notes, createdAt, updatedAt, sets,
                null, null, null);
    }
}
