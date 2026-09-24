package com.gymflow.model;

import java.math.BigDecimal;

/** User-entered values for one ordered Workout set. */
public record WorkoutSetInput(String exerciseName, Integer repetitions, Integer durationSeconds,
        BigDecimal resistanceKilograms) {
}
