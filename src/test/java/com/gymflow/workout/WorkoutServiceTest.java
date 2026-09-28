package com.gymflow.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

    private static SaveWorkoutRequest request(String startedAt, String endedAt) {
        return new SaveWorkoutRequest(Instant.parse(startedAt), Instant.parse(endedAt), null,
                List.of(new WorkoutSetInput("Squat", 8, null, new BigDecimal("60"))));
    }
}
