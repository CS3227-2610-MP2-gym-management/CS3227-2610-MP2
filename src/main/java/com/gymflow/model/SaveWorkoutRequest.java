package com.gymflow.model;

import java.time.Instant;
import java.util.List;

/** Complete replacement payload for a saved Workout. */
public record SaveWorkoutRequest(Instant performedAt, String notes, List<WorkoutSetInput> sets) {
}
