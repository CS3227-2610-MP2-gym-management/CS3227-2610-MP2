package com.gymflow.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.member.CreateMemberRequest;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.Account;
import com.gymflow.model.Member;
import com.gymflow.model.PaymentMethod;
import com.gymflow.model.Visit;
import com.gymflow.model.VisitOverview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnerVisitServiceTest {
    @TempDir
    Path directory;
    private Path databaseFile;
    private OwnerVisitService visits;
    private Account owner;
    private Member alice;
    private Member bob;

    @BeforeEach
    void setUp() throws Exception {
        databaseFile = directory.resolve("gymflow.db");
        GymFlowDatabase database = new GymFlowDatabase(databaseFile);
        database.initialize();
        owner = new AuthenticationService(database)
                .createOwner("owner@example.com", "owner password".toCharArray());
        OwnerMemberService members = new OwnerMemberService(database);
        alice = members.createMember(member("alice@example.com", "Alice Tan", "81234567"), owner.id());
        bob = members.createMember(member("bob@example.com", "Bob Lee", "92345678"), owner.id());
        visits = new OwnerVisitService(database);
    }

    @Test
    void searchesAllVisitsByMemberNameOrEmailInNewestFirstOrder() throws Exception {
        insertVisit(1, alice.accountId(), "2026-09-15T01:00:00.000Z", "2026-09-15T02:00:00.000Z");
        insertVisit(2, bob.accountId(), "2026-09-15T03:00:00.000Z", null);
        insertVisit(3, alice.accountId(), "2026-09-15T03:00:00.000Z", "2026-09-15T04:00:00.000Z");
        insertVisit(4, alice.accountId(), "2026-09-15T03:00:00.500Z", "2026-09-15T04:00:00.000Z");

        List<VisitOverview> all = visits.searchVisits("", false);

        assertEquals(List.of(4L, 3L, 2L, 1L), all.stream().map(item -> item.visit().id()).toList());
        assertEquals(3, visits.searchVisits("ALICE", false).size());
        assertEquals(1, visits.searchVisits("bob@", false).size());
        assertEquals(0, visits.searchVisits(alice.memberNumber(), false).size());
        assertEquals(0, visits.searchVisits("8123", false).size());
        assertEquals("Alice Tan", all.getFirst().memberName());
        assertEquals("alice@example.com", all.getFirst().memberEmail());
    }

    @Test
    void currentlyVisitingReturnsOnlyOpenVisitsAndCountsThem() throws Exception {
        insertVisit(1, alice.accountId(), "2026-09-15T01:00:00.000Z", "2026-09-15T02:00:00.000Z");
        insertVisit(2, bob.accountId(), "2026-09-15T03:00:00.000Z", null);

        Visit open = visits.searchVisits("", true).getFirst().visit();

        assertEquals(2, open.id());
        assertNull(open.exitedAt());
        assertEquals(1, visits.currentVisitorCount());
    }

    @Test
    void memberHistoryMapsInstantsAndExcludesOtherMembers() throws Exception {
        insertVisit(1, alice.accountId(), "2026-09-15T01:00:00.000Z", "2026-09-15T02:00:00.000Z");
        insertVisit(2, bob.accountId(), "2026-09-15T03:00:00.000Z", null);

        Visit visit = visits.visitHistory(alice.accountId()).getFirst();

        assertEquals(alice.accountId(), visit.memberId());
        assertEquals(Instant.parse("2026-09-15T01:00:00Z"), visit.enteredAt());
        assertEquals(Instant.parse("2026-09-15T02:00:00Z"), visit.exitedAt());
        assertEquals(Instant.parse("2026-09-15T01:00:00Z"), visit.createdAt());
        assertEquals(1, visits.visitHistory(alice.accountId()).size());
        assertNull(visit.correctedAt());
        assertNull(visit.correctedByUserId());
        assertNull(visit.correctionReason());
    }

    @Test
    void ownerCorrectsVisitAndRecordsLatestReason() throws Exception {
        insertVisit(1, alice.accountId(), "2026-09-15T01:00:00.000Z", null);

        Visit corrected = visits.correctVisit(1,
                Instant.parse("2026-09-15T01:15:00Z"),
                Instant.parse("2026-09-15T02:30:00Z"), "  Member forgot to exit  ", owner.id());

        assertEquals(Instant.parse("2026-09-15T01:15:00Z"), corrected.enteredAt());
        assertEquals(Instant.parse("2026-09-15T02:30:00Z"), corrected.exitedAt());
        assertEquals(owner.id(), corrected.correctedByUserId());
        assertEquals("Member forgot to exit", corrected.correctionReason());
        assertTrue(corrected.correctedAt() != null);
        assertEquals(0, visits.currentVisitorCount());
    }

    @Test
    void rejectsInvalidOrUnauthorizedCorrectionsWithoutChangingVisit() throws Exception {
        insertVisit(1, alice.accountId(), "2026-09-15T01:00:00.000Z", "2026-09-15T02:00:00.000Z");
        Instant entry = Instant.parse("2026-09-15T01:00:00Z");
        Instant exit = Instant.parse("2026-09-15T02:00:00Z");

        assertThrows(IllegalArgumentException.class,
                () -> visits.correctVisit(1, entry, exit, "unchanged", owner.id()));
        assertThrows(IllegalArgumentException.class,
                () -> visits.correctVisit(1, exit, entry, "reversed", owner.id()));
        assertThrows(IllegalArgumentException.class,
                () -> visits.correctVisit(1, entry, null, " ", owner.id()));
        assertThrows(IllegalArgumentException.class,
                () -> visits.correctVisit(999, entry, null, "missing", owner.id()));
        assertThrows(IllegalArgumentException.class,
                () -> visits.correctVisit(1, entry, null, "unauthorized", alice.accountId()));

        Visit unchanged = visits.visitHistory(alice.accountId()).getFirst();
        assertEquals(entry, unchanged.enteredAt());
        assertEquals(exit, unchanged.exitedAt());
        assertNull(unchanged.correctedAt());
    }

    @Test
    void rejectsReopeningWhenMemberAlreadyHasAnotherOpenVisit() throws Exception {
        insertVisit(1, alice.accountId(), "2026-09-15T01:00:00.000Z", "2026-09-15T02:00:00.000Z");
        insertVisit(2, alice.accountId(), "2026-09-15T03:00:00.000Z", null);

        assertThrows(IllegalArgumentException.class, () -> visits.correctVisit(1,
                Instant.parse("2026-09-15T01:00:00Z"), null, "Exit was recorded by mistake", owner.id()));

        assertEquals(1, visits.currentVisitorCount());
    }

    private CreateMemberRequest member(String email, String name, String phone) {
        return new CreateMemberRequest(email, "member password".toCharArray(), name, phone, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                new BigDecimal("50.00"), PaymentMethod.CARD, Instant.parse("2026-09-01T00:00:00Z"), "");
    }

    private void insertVisit(long id, long memberId, String enteredAt, String exitedAt) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO visits(id, member_account_id, entered_at, exited_at, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
            statement.setLong(1, id);
            statement.setLong(2, memberId);
            statement.setString(3, enteredAt);
            statement.setString(4, exitedAt);
            statement.setString(5, enteredAt);
            statement.executeUpdate();
        }
    }
}
