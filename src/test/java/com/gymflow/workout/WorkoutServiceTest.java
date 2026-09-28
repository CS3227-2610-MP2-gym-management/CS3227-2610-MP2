package com.gymflow.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.member.CreateMemberRequest;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.Account;
import com.gymflow.model.PaymentMethod;
import com.gymflow.model.SaveWorkoutRequest;
import com.gymflow.model.Workout;
import com.gymflow.model.WorkoutSetInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkoutServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");
    @TempDir Path directory;
    private Account member;
    private Account owner;
    private OwnerMemberService members;
    private WorkoutService workouts;

    @BeforeEach
    void setUp() {
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        database.initialize();
        AuthenticationService authentication = new AuthenticationService(database);
        owner = authentication.createOwner("owner@example.com", "owner password".toCharArray());
        members = new OwnerMemberService(database);
        members.createMember(new CreateMemberRequest("member@example.com",
                "member password".toCharArray(), "Member Tan", "81234567", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), new BigDecimal("50"),
                PaymentMethod.CARD, NOW, ""), owner.id());
        member = authentication.authenticate("member@example.com", "member password".toCharArray()).orElseThrow();
        workouts = new WorkoutService(database, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void savesCrossMidnightWorkoutAndOrdersByEndTime() {
        Workout first = workouts.create(member, request("2026-09-14T23:45:00Z", "2026-09-15T00:45:00Z"));
        Workout second = workouts.create(member, request("2026-09-15T08:45:00Z", "2026-09-15T09:45:00Z"));

        List<Workout> history = workouts.history(member);

        assertEquals(second.id(), history.getFirst().id());
        assertEquals(first.id(), history.getLast().id());
        assertEquals(Instant.parse("2026-09-14T23:45:00Z"), first.startedAt());
        assertEquals(Instant.parse("2026-09-15T00:45:00Z"), first.endedAt());
    }

    @Test
    void rejectsEqualReversedAndFutureWorkoutRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> workouts.create(member, request("2026-09-15T09:00:00Z", "2026-09-15T09:00:00Z")));
        assertThrows(IllegalArgumentException.class,
                () -> workouts.create(member, request("2026-09-15T09:30:00Z", "2026-09-15T09:00:00Z")));
        assertThrows(IllegalArgumentException.class,
                () -> workouts.create(member, request("2026-09-15T09:00:00Z", "2026-09-15T10:15:00Z")));
    }

    @Test
    void persistsAnEmptyOpenWorkoutAcrossServiceRestart() {
        Workout open = workouts.create(member, new SaveWorkoutRequest(NOW, null, "", List.of()));

        GymFlowDatabase restartedDatabase = new GymFlowDatabase(directory.resolve("gymflow.db"));
        restartedDatabase.initialize();
        Workout reloaded = new WorkoutService(restartedDatabase, Clock.fixed(NOW, ZoneOffset.UTC))
                .history(member).getFirst();

        assertEquals(open.id(), reloaded.id());
        assertEquals(null, reloaded.endedAt());
        assertEquals(List.of(), reloaded.sets());
    }

    @Test
    void keepsSessionTimesImmutableWhenReplacingExercises() {
        Workout saved = workouts.create(member, request("2026-09-15T08:00:00Z", "2026-09-15T09:00:00Z"));

        Workout updated = workouts.update(member, saved.id(),
                request("2026-09-15T07:00:00Z", "2026-09-15T09:30:00Z"));

        assertEquals(saved.startedAt(), updated.startedAt());
        assertEquals(saved.endedAt(), updated.endedAt());
    }

    @Test
    void requiresCurrentMembershipOnlyWhenCreatingWorkout() {
        Workout saved = workouts.create(member, request("2026-09-15T08:00:00Z", "2026-09-15T09:00:00Z"));
        var membership = members.membershipHistory(member.id()).getFirst();
        members.setMembershipActive(membership.id(), false, owner.id());

        assertThrows(IllegalArgumentException.class,
                () -> workouts.create(member, request("2026-09-15T09:00:00Z", "2026-09-15T09:30:00Z")));
        assertEquals(saved.id(), workouts.history(member).getFirst().id());
        assertEquals(saved.id(), workouts.update(member, saved.id(),
                request("2026-09-15T07:00:00Z", "2026-09-15T09:30:00Z")).id());
    }

    @Test
    void rejectsInvalidSetValuesWithoutSavingAWorkout() {
        List<WorkoutSetInput> invalid = List.of(
                new WorkoutSetInput(" ", 8, null, null),
                new WorkoutSetInput("Squat", 0, null, null),
                new WorkoutSetInput("Squat", -1, null, null),
                new WorkoutSetInput("Plank", null, 0, null),
                new WorkoutSetInput("Plank", null, -1, null),
                new WorkoutSetInput("Squat", 8, 30, null),
                new WorkoutSetInput("Squat", 8, null, new BigDecimal("-1")),
                new WorkoutSetInput("Squat", 8, null, new BigDecimal("1.0001")));
        for (WorkoutSetInput set : invalid) {
            SaveWorkoutRequest request = new SaveWorkoutRequest(NOW.minusSeconds(3600),
                    NOW.minusSeconds(1800), "notes", List.of(set));
            assertThrows(IllegalArgumentException.class, () -> workouts.create(member, request));
        }
        assertTrue(workouts.history(member).isEmpty());
    }

    @Test
    void rejectsMissingRequestDataAndUnauthorizedActors() {
        assertThrows(IllegalArgumentException.class, () -> workouts.create(member, null));
        assertThrows(IllegalArgumentException.class, () -> workouts.create(member,
                new SaveWorkoutRequest(null, NOW, null, List.of())));
        assertThrows(IllegalArgumentException.class, () -> workouts.create(member,
                new SaveWorkoutRequest(NOW.minusSeconds(60), NOW, null, null)));
        assertThrows(IllegalArgumentException.class, () -> workouts.history(owner));
        assertThrows(IllegalArgumentException.class, () -> workouts.history(null));
    }

    @Test
    void checkoutRequiresOneMinuteAndPreservesOpenDraftOnFailure() {
        Workout open = workouts.create(member, new SaveWorkoutRequest(NOW, null, "draft",
                List.of(new WorkoutSetInput("Squat", 8, null, null))));
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        WorkoutService early = new WorkoutService(database,
                Clock.fixed(NOW.plusSeconds(59), ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> early.checkOut(member, open.id(),
                new SaveWorkoutRequest(open.startedAt(), null, "changed", List.of())));
        Workout retained = workouts.history(member).getFirst();
        assertEquals(null, retained.endedAt());
        assertEquals("draft", retained.notes());
        assertEquals(1, retained.sets().size());

        WorkoutService onTime = new WorkoutService(database,
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
        Workout closed = onTime.checkOut(member, open.id(),
                new SaveWorkoutRequest(open.startedAt(), null, "completed", List.of()));
        assertEquals(NOW.plusSeconds(60), closed.endedAt());
        assertThrows(IllegalArgumentException.class, () -> onTime.checkOut(member, open.id(),
                new SaveWorkoutRequest(open.startedAt(), null, "again", List.of())));
    }

    @Test
    void singaporeCalendarDateControlsMembershipExpiry() {
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        Instant boundary = Instant.parse("2026-09-30T16:00:00Z");
        WorkoutService singapore = new WorkoutService(database,
                Clock.fixed(boundary, ZoneId.of("Asia/Singapore")));
        assertThrows(IllegalArgumentException.class,
                () -> singapore.create(member,
                        request("2026-09-30T14:00:00Z", "2026-09-30T15:00:00Z")));
    }

    private static SaveWorkoutRequest request(String startedAt, String endedAt) {
        return new SaveWorkoutRequest(Instant.parse(startedAt), Instant.parse(endedAt), null,
                List.of(new WorkoutSetInput("Squat", 8, null, new BigDecimal("60"))));
    }
}
