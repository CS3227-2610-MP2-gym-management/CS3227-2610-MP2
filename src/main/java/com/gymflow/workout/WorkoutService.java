package com.gymflow.workout;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.WorkoutStore;
import com.gymflow.model.Account;
import com.gymflow.model.Role;
import com.gymflow.model.SaveWorkoutRequest;
import com.gymflow.model.Workout;
import com.gymflow.model.WorkoutSetInput;

/** Validates and authorizes Member Workout commands. */
public final class WorkoutService {
    private final WorkoutStore store;
    private final Clock clock;

    /** Creates a service using the system clock. */
    public WorkoutService(GymFlowDatabase database) {
        this(database, Clock.systemDefaultZone());
    }

    /** Creates a service using the supplied clock. */
    public WorkoutService(GymFlowDatabase database, Clock clock) {
        store = new WorkoutStore(Objects.requireNonNull(database));
        this.clock = Objects.requireNonNull(clock);
    }

    /** Lists the Member's Workouts. */
    public List<Workout> history(Account actor) {
        requireMember(actor);
        return store.findByMember(actor.id());
    }

    /** Saves a new completed Workout. */
    public Workout create(Account actor, SaveWorkoutRequest request) {
        requireMember(actor);
        return store.create(actor.id(), validate(request), clock.instant());
    }

    /** Replaces a saved Workout and all of its sets. */
    public Workout update(Account actor, long id, SaveWorkoutRequest request) {
        requireMember(actor);
        return store.update(actor.id(), id, validate(request), clock.instant());
    }

    /** Deletes a saved Workout and its sets. */
    public void delete(Account actor, long id) {
        requireMember(actor);
        store.delete(actor.id(), id);
    }

    private SaveWorkoutRequest validate(SaveWorkoutRequest request) {
        if (request == null || request.startedAt() == null || request.endedAt() == null
                || !request.startedAt().isBefore(request.endedAt())) {
            throw new IllegalArgumentException("Workout start time must be before its end time");
        }
        if (request.endedAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException("Workout end time cannot be in the future");
        }
        if (request.sets() == null || request.sets().isEmpty()) {
            throw new IllegalArgumentException("A Workout needs at least one set");
        }
        List<WorkoutSetInput> sets = request.sets().stream().map(this::validateSet).toList();
        String notes = request.notes() == null ? "" : request.notes().trim();
        return new SaveWorkoutRequest(request.startedAt(), request.endedAt(),
                notes.isEmpty() ? null : notes, sets);
    }

    private WorkoutSetInput validateSet(WorkoutSetInput set) {
        if (set == null || set.exerciseName() == null || set.exerciseName().trim().isEmpty()) {
            throw new IllegalArgumentException("Exercise name is required");
        }
        boolean repetitions = set.repetitions() != null && set.repetitions() > 0;
        boolean duration = set.durationSeconds() != null && set.durationSeconds() > 0;
        if (repetitions == duration) {
            throw new IllegalArgumentException("Provide exactly one positive measure");
        }
        if ((set.repetitions() != null && !repetitions)
                || (set.durationSeconds() != null && !duration)
                || (set.resistanceKilograms() != null && (set.resistanceKilograms().signum() < 0
                || set.resistanceKilograms().scale() > 3))) {
            throw new IllegalArgumentException("Set values are invalid");
        }
        return new WorkoutSetInput(set.exerciseName().trim(), set.repetitions(),
                set.durationSeconds(), set.resistanceKilograms() == null ? null
                        : set.resistanceKilograms().stripTrailingZeros());
    }

    private static void requireMember(Account actor) {
        if (actor == null || actor.role() != Role.MEMBER || !actor.active()) {
            throw new IllegalArgumentException("An active Member account is required");
        }
    }
}
