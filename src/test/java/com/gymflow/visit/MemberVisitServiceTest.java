package com.gymflow.visit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

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
        assertThrows(IllegalArgumentException.class, () -> visits.checkOut(alice));
        MemberVisitService laterVisits = new MemberVisitService(
                new GymFlowDatabase(directory.resolve("gymflow.db")),
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
        assertTrue(laterVisits.checkOut(alice).exitedAt().equals(NOW.plusSeconds(60)));
        assertFalse(laterVisits.currentState(alice).checkedIn());
        assertThrows(IllegalArgumentException.class, () -> laterVisits.checkOut(alice));
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

    @Test
    void returnsOnlyTheMembersVisitsInNewestFirstOrder() throws Exception {
        insertVisit(alice.id(), "2026-09-14T10:00:00.000Z", "2026-09-14T11:00:00.000Z");
        insertVisit(alice.id(), "2026-09-15T09:00:00.000Z", null);
        Member other = members.createMember(new CreateMemberRequest("other@example.com",
                "member password".toCharArray(), "Other Member", "81234568", null,
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15), new BigDecimal("50.00"),
                PaymentMethod.CARD, NOW, ""), 1);
        insertVisit(other.accountId(), "2026-09-16T10:00:00.000Z", null);

        List<com.gymflow.model.Visit> history = visits.history(alice);

        assertEquals(2, history.size());
        assertEquals(2, history.getFirst().id());
        assertEquals(null, history.getFirst().exitedAt());
        assertEquals(1, history.getLast().id());
    }

    private void insertVisit(long memberId, String enteredAt, String exitedAt) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + directory.resolve("gymflow.db").toAbsolutePath());
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO workouts (member_account_id, started_at, ended_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
            statement.setLong(1, memberId);
            statement.setString(2, enteredAt);
            statement.setString(3, exitedAt);
            statement.setString(4, enteredAt);
            statement.setString(5, enteredAt);
            statement.executeUpdate();
        }
    }

    private static CreateMemberRequest request() {
        return new CreateMemberRequest("alice@example.com", "member password".toCharArray(), "Alice Tan", "81234567",
                null, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15), new BigDecimal("50.00"),
                PaymentMethod.CARD, NOW, "");
    }
}
