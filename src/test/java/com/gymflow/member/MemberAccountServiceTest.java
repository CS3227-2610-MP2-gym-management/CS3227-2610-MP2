package com.gymflow.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.model.Account;
import com.gymflow.model.Member;
import com.gymflow.model.MembershipNoticeState;
import com.gymflow.model.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemberAccountServiceTest {
    @TempDir
    Path directory;

    @Test
    void loadsOnlyTheAuthenticatedMembersProfileAndOrderedHistory() {
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        database.initialize();
        AuthenticationService authentication = new AuthenticationService(database);
        Account owner = authentication.createOwner("owner@example.com", "owner password".toCharArray());
        OwnerMemberService ownerMembers = new OwnerMemberService(database);
        Member alice = ownerMembers.createMember(request("alice@example.com", LocalDate.of(2026, 9, 1)), owner.id());
        Member bob = ownerMembers.createMember(request("bob@example.com", LocalDate.of(2026, 8, 1)), owner.id());
        Member charlie = ownerMembers.createMember(
                request("charlie@example.com", LocalDate.of(2026, 7, 1)), owner.id());
        ownerMembers.addMembership(new AddMembershipRequest(alice.accountId(), LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), new BigDecimal("90.00"), PaymentMethod.CARD,
                Instant.parse("2026-09-20T00:00:00Z"), "R-2"), owner.id());
        ownerMembers.addMembership(new AddMembershipRequest(bob.accountId(), LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), new BigDecimal("90.00"), PaymentMethod.CARD,
                Instant.parse("2026-09-20T00:00:00Z"), "R-3"), owner.id());
        Account actor = authentication.authenticate("alice@example.com", "member password".toCharArray()).orElseThrow();
        Account bobActor = authentication.authenticate("bob@example.com", "member password".toCharArray())
                .orElseThrow();
        Account charlieActor = authentication.authenticate("charlie@example.com", "member password".toCharArray())
                .orElseThrow();
        MemberAccountService service = new MemberAccountService(database,
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));

        var overview = service.loadOverview(actor);

        assertEquals(alice, overview.member());
        assertEquals(List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 1)),
                overview.memberships().stream().map(item -> item.startDate()).toList());
        assertEquals(MembershipNoticeState.ACTIVE, service.membershipNotice(overview).state());
        assertEquals(MembershipNoticeState.UPCOMING, service.membershipNotice(service.loadOverview(bobActor)).state());
        assertEquals(MembershipNoticeState.RENEWAL_NEEDED,
                service.membershipNotice(service.loadOverview(charlieActor)).state());
        assertThrows(IllegalArgumentException.class, () -> service.loadOverview(owner));
        assertEquals(bob.accountId(), ownerMembers.searchMembers("bob").getFirst().accountId());
    }

    @Test
    void updatesOnlyAnActorsContactDetailsAndReplacesTheirVerifiedPassword() {
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        database.initialize();
        AuthenticationService authentication = new AuthenticationService(database);
        Account owner = authentication.createOwner("owner@example.com", "owner password".toCharArray());
        OwnerMemberService ownerMembers = new OwnerMemberService(database);
        Member alice = ownerMembers.createMember(request("alice@example.com", LocalDate.of(2026, 9, 1)), owner.id());
        Member bob = ownerMembers.createMember(request("bob@example.com", LocalDate.of(2026, 9, 1)), owner.id());
        Account actor = authentication.authenticate("alice@example.com", "member password".toCharArray()).orElseThrow();
        MemberAccountService service = new MemberAccountService(database);

        char[] contactPassword = "member password".toCharArray();
        Member updated = service.updateContact(actor, "  ALICE.NEW@EXAMPLE.COM ",
                "+65 8123-4567", contactPassword);
        assertArrayEquals(new char[contactPassword.length], contactPassword);
        assertEquals("alice.new@example.com", updated.email());
        assertEquals("+65 8123 4567", updated.phoneNumber());
        assertEquals(alice.memberNumber(), updated.memberNumber());
        assertEquals(alice.fullName(), updated.fullName());
        assertEquals(alice.dateOfBirth(), updated.dateOfBirth());
        assertEquals(bob, service.loadOverview(authentication.authenticate("bob@example.com",
                "member password".toCharArray()).orElseThrow()).member());

        char[] current = "member password".toCharArray();
        char[] replacement = "a secure replacement password".toCharArray();
        service.changePassword(actor, current, replacement);
        assertArrayEquals(new char[current.length], current);
        assertArrayEquals(new char[replacement.length], replacement);
        assertFalse(authentication.authenticate("alice.new@example.com", "member password".toCharArray()).isPresent());
        assertTrue(authentication.authenticate("alice.new@example.com", "a secure replacement password".toCharArray())
                .isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> service.updateContact(actor, "bob@example.com", "81234567",
                        "member password".toCharArray()));
    }

    @Test
    void rejectsWrongCurrentPasswordWithoutChangingCredentials() {
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        database.initialize();
        AuthenticationService authentication = new AuthenticationService(database);
        Account owner = authentication.createOwner("owner@example.com", "owner password".toCharArray());
        OwnerMemberService ownerMembers = new OwnerMemberService(database);
        ownerMembers.createMember(request("alice@example.com", LocalDate.of(2026, 9, 1)), owner.id());
        Account actor = authentication.authenticate("alice@example.com", "member password".toCharArray()).orElseThrow();
        MemberAccountService service = new MemberAccountService(database);
        char[] current = "wrong password".toCharArray();
        char[] replacement = "a secure replacement password".toCharArray();

        assertThrows(IllegalArgumentException.class, () -> service.changePassword(actor, current, replacement));
        assertArrayEquals(new char[current.length], current);
        assertArrayEquals(new char[replacement.length], replacement);
        assertTrue(authentication.authenticate("alice@example.com", "member password".toCharArray()).isPresent());
    }

    private static CreateMemberRequest request(String email, LocalDate start) {
        return new CreateMemberRequest(email, "member password".toCharArray(), "Alice Tan", "81234567",
                LocalDate.of(1995, 3, 4), start, start.plusMonths(1).minusDays(1), new BigDecimal("120.00"),
                PaymentMethod.CARD, Instant.parse("2026-09-01T10:00:00Z"), "R-1");
    }
}
