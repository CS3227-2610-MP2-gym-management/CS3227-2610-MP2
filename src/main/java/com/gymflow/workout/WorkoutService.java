package com.gymflow.workout;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.MemberAccountStore;
import com.gymflow.data.WorkoutStore;
import com.gymflow.data.SupabaseDataClient;
import com.gymflow.data.SupabaseMemberAccountStore;
import com.gymflow.data.SupabaseWorkoutStore;
import com.gymflow.model.Account;
import com.gymflow.model.MembershipStatus;
import com.gymflow.model.Role;
import com.gymflow.model.SaveWorkoutRequest;
import com.gymflow.model.Workout;
import com.gymflow.model.WorkoutSetInput;

/** Validates and authorizes Member Workout commands. */
public final class WorkoutService {
    private final WorkoutStore store;
    private final SupabaseWorkoutStore cloudStore;
    private final Clock clock;
    private final MemberAccountStore memberAccounts;
    private final SupabaseMemberAccountStore cloudMemberAccounts;

    /** Creates a service using the system clock. */
    public WorkoutService(GymFlowDatabase database) {
        this(database, Clock.systemDefaultZone());
    }

    /** Creates a service using the supplied clock. */
    public WorkoutService(GymFlowDatabase database, Clock clock) {
        store = new WorkoutStore(Objects.requireNonNull(database));
        cloudStore = null;
        memberAccounts = new MemberAccountStore(database);
        cloudMemberAccounts = null;
        this.clock = Objects.requireNonNull(clock);
    }

    /** Creates a Supabase-backed service using the system clock. */
    public WorkoutService(SupabaseDataClient client) {
        store = null;
        cloudStore = new SupabaseWorkoutStore(Objects.requireNonNull(client));
        memberAccounts = null;
        cloudMemberAccounts = new SupabaseMemberAccountStore(client);
        clock = Clock.systemDefaultZone();
    }

    /** Lists the Member's Workouts. */
    public List<Workout> history(Account actor) {
        requireMember(actor);
        return cloudStore == null ? store.findByMember(actor.id()) : cloudStore.findByMember(actor.id());
    }

    /** Saves a new completed Workout. */
    public Workout create(Account actor, SaveWorkoutRequest request) {
        requireMember(actor);
        requireCurrentMembership(actor);
        SaveWorkoutRequest validated = validate(request);
        return cloudStore == null
                ? store.create(actor.id(), validated, clock.instant())
                : cloudStore.create(actor.id(), validated, clock.instant());
    }

    /** Replaces a saved Workout and all of its sets. */
    public Workout update(Account actor, long id, SaveWorkoutRequest request) {
        requireMember(actor);
        SaveWorkoutRequest validated = validate(request);
        return cloudStore == null
                ? store.update(actor.id(), id, validated, clock.instant())
                : cloudStore.update(actor.id(), id, validated, clock.instant());
    }

    /** Atomically saves an open Workout draft and records its check-out time. */
    public Workout checkOut(Account actor, long id, SaveWorkoutRequest request) {
        requireMember(actor);
        SaveWorkoutRequest validated = validate(request);
        return cloudStore == null
                ? store.checkOut(actor.id(), id, validated, clock.instant())
                : cloudStore.checkOut(actor.id(), id, validated, clock.instant());
    }

    /** Deletes a saved Workout and its sets. */
    public void delete(Account actor, long id) {
        requireMember(actor);
        if (cloudStore == null) {
            store.delete(actor.id(), id);
        } else {
            cloudStore.delete(actor.id(), id);
        }
    }

    private SaveWorkoutRequest validate(SaveWorkoutRequest request) {
        if (request == null || request.startedAt() == null
                || (request.endedAt() != null && !request.startedAt().isBefore(request.endedAt()))) {
            throw new IllegalArgumentException("Workout start time must be before its end time");
        }
        if (request.endedAt() != null && request.endedAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException("Workout end time cannot be in the future");
        }
        if (request.sets() == null) {
            throw new IllegalArgumentException("Workout sets are required");
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
        if (repetitions && duration) {
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

    private void requireCurrentMembership(Account actor) {
        boolean current = (cloudMemberAccounts == null
                ? memberAccounts.membershipHistory(actor.id())
                : cloudMemberAccounts.membershipHistory(actor.id())).stream()
                .anyMatch(item -> item.status(java.time.LocalDate.now(clock)) == MembershipStatus.ACTIVE);
        if (!current) {
            throw new IllegalArgumentException("A current Membership is required to record a Workout");
        }
    }
}
