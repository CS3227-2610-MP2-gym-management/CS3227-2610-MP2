package com.gymflow.visit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.member.CreateMemberRequest;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.Account;
import com.gymflow.model.Member;
import com.gymflow.model.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemberVisitServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");
    @TempDir Path directory;
    private OwnerMemberService members;
    private MemberVisitService visits;
    private Account alice;

    @BeforeEach
    void setUp() {
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        database.initialize();
        Account owner = new AuthenticationService(database)
                .createOwner("owner@example.com", "owner password".toCharArray());
        members = new OwnerMemberService(database);
        Member member = members.createMember(request(), owner.id());
        alice = new AuthenticationService(database)
                .authenticate("alice@example.com", "member password".toCharArray()).orElseThrow();
        visits = new MemberVisitService(database, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void checksInOnceAndDerivesState() {
        assertFalse(visits.currentState(alice).checkedIn());
        visits.checkIn(alice);
        assertTrue(visits.currentState(alice).checkedIn());
        assertThrows(IllegalArgumentException.class, () -> visits.checkIn(alice));
    }

    @Test
    void checksOutAfterMembershipIsDeactivated() {
        visits.checkIn(alice);
        members.setMembershipActive(1, false, 1);
        assertTrue(visits.checkOut(alice).exitedAt().equals(NOW));
        assertFalse(visits.currentState(alice).checkedIn());
        assertThrows(IllegalArgumentException.class, () -> visits.checkOut(alice));
    }

    @Test
    void checksMembershipStartAndExpiryBoundaries() {
        visits.checkIn(alice);
        assertTrue(visits.currentState(alice).checkedIn());
    }

    @Test
    void rejectsAnOwnerActor() {
        Account owner = new AuthenticationService(new GymFlowDatabase(directory.resolve("gymflow.db")))
                .authenticate("owner@example.com", "owner password".toCharArray()).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> visits.currentState(owner));
    }

    private static CreateMemberRequest request() {
        return new CreateMemberRequest("alice@example.com", "member password".toCharArray(), "Alice Tan", "81234567",
                null, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15), new BigDecimal("50.00"),
                PaymentMethod.CARD, NOW, "");
    }
}
