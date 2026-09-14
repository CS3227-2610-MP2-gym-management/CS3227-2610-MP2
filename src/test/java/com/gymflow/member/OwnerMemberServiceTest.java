package com.gymflow.member;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.model.Account;
import com.gymflow.model.Member;
import com.gymflow.model.MemberPayment;
import com.gymflow.model.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnerMemberServiceTest {
    @TempDir
    Path directory;
    private AuthenticationService authentication;
    private OwnerMemberService members;
    private Account owner;
    private Path databaseFile;

    @BeforeEach
    void setUp() {
        databaseFile = directory.resolve("gymflow.db");
        GymFlowDatabase database = new GymFlowDatabase(databaseFile);
        database.initialize();
        authentication = new AuthenticationService(database);
        owner = authentication.createOwner("owner@example.com", "owner password".toCharArray());
        members = new OwnerMemberService(database);
    }

    @Test
    void onboardingAtomicallyCreatesMemberMembershipAndPayment() throws Exception {
        char[] password = "member password".toCharArray();

        Member member = members.createMember(request("ALICE@EXAMPLE.COM", password), owner.id());

        assertEquals("M000001", member.memberNumber());
        assertEquals("alice@example.com", member.email());
        assertEquals("Alice Tan", member.fullName());
        assertArrayEquals(new char[password.length], password);
        assertTrue(authentication.authenticate("alice@example.com", "member password".toCharArray()).isPresent());
        assertEquals(1, count("memberships"));
        assertEquals(1, count("payments"));
    }

    @Test
    void failedFinalInsertRollsBackAllOnboardingRecords() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TRIGGER reject_payment BEFORE INSERT ON payments
                    BEGIN SELECT RAISE(ABORT, 'reject payment'); END
                    """);
        }

        assertThrows(IllegalStateException.class, () -> members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id()));

        assertTrue(members.searchMembers("").isEmpty());
        assertEquals(0, count("memberships"));
        assertEquals(0, count("payments"));
    }

    @Test
    void duplicateEmailIsAValidationFailureWithoutPartialRecords() throws Exception {
        members.createMember(request("alice@example.com", "member password".toCharArray()), owner.id());

        assertThrows(IllegalArgumentException.class,
                () -> members.createMember(request("ALICE@EXAMPLE.COM", "another password".toCharArray()), owner.id()));

        assertEquals(1, members.searchMembers("").size());
        assertEquals(1, count("memberships"));
        assertEquals(1, count("payments"));
    }

    @Test
    void listsPaymentHistoryForMember() {
        Member member = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());

        MemberPayment payment = members.paymentHistory(member.accountId()).getFirst();

        assertEquals(new BigDecimal("120.00"), payment.amount());
        assertEquals(PaymentMethod.CARD, payment.method());
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), payment.paidAt());
        assertEquals("R-001", payment.reference());
    }

    @Test
    void searchesByNameAndEmailOnlyThenEditsProfileWithoutChangingMemberNumber() {
        Member created = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());

        assertEquals(1, members.searchMembers("ALICE").size());
        assertEquals(1, members.searchMembers("example.com").size());
        assertTrue(members.searchMembers("M000001").isEmpty());
        assertTrue(members.searchMembers("8123").isEmpty());
        assertTrue(members.searchMembers("missing").isEmpty());

        Member updated = members.updateMember(created.accountId(), "new@example.com", "Alice Lim",
                "+65 8111 2222", LocalDate.of(1995, 3, 4));

        assertEquals(created.memberNumber(), updated.memberNumber());
        assertEquals("new@example.com", updated.email());
        assertEquals("Alice Lim", updated.fullName());
        assertFalse(members.searchMembers("new@").isEmpty());
    }

    @Test
    void normalizesSingaporePhoneAndAcceptsMemberExactlyTwelveYearsOld() {
        CreateMemberRequest request = request("alice@example.com", "member password".toCharArray(),
                "81234567", LocalDate.now().minusYears(12));

        Member member = members.createMember(request, owner.id());

        assertEquals("+65 8123 4567", member.phoneNumber());
    }

    @Test
    void acceptsMissingDateOfBirth() {
        Member member = members.createMember(request("alice@example.com", "member password".toCharArray(),
                "81234567", null), owner.id());

        assertNull(member.dateOfBirth());
    }

    @Test
    void rejectsUnsupportedSingaporePhoneNumbers() {
        for (String phone : new String[] {"8123456", "812345678", "51234567", "8123ABCD", "+1 81234567"}) {
            assertThrows(IllegalArgumentException.class, () -> members.createMember(
                    request("alice@example.com", "member password".toCharArray(), phone, null), owner.id()));
        }
    }

    @Test
    void rejectsMemberYoungerThanTwelve() {
        assertThrows(IllegalArgumentException.class, () -> members.createMember(
                request("alice@example.com", "member password".toCharArray(),
                        "81234567", LocalDate.now().minusYears(12).plusDays(1)), owner.id()));
    }

    @Test
    void appliesPhoneAndAgeValidationWhenEditing() {
        Member member = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());

        assertThrows(IllegalArgumentException.class, () -> members.updateMember(member.accountId(),
                member.email(), member.fullName(), "51234567", member.dateOfBirth()));
        assertThrows(IllegalArgumentException.class, () -> members.updateMember(member.accountId(),
                member.email(), member.fullName(), "81234567", LocalDate.now().minusYears(12).plusDays(1)));
    }

    @Test
    void editingToAnotherMembersEmailIsAValidationFailure() {
        Member alice = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());
        members.createMember(request("bob@example.com", "another password".toCharArray()), owner.id());

        assertThrows(IllegalArgumentException.class, () -> members.updateMember(alice.accountId(),
                "BOB@EXAMPLE.COM", "Alice Tan", "+65 8123 4567", LocalDate.of(1995, 3, 4)));

        assertEquals("alice@example.com", members.searchMembers("alice").getFirst().email());
    }

    @Test
    void rejectsInvalidOnboardingFields() {
        char[] shortPassword = "short".toCharArray();
        assertThrows(IllegalArgumentException.class,
                () -> members.createMember(request("bad-email", "member password".toCharArray()), owner.id()));
        assertThrows(IllegalArgumentException.class,
                () -> members.createMember(request("member@example.com", shortPassword), owner.id()));
        assertArrayEquals(new char[shortPassword.length], shortPassword);
        assertThrows(IllegalArgumentException.class,
                () -> members.createMember(requestWithExpiry(LocalDate.of(2026, 1, 1)), owner.id()));
    }

    @Test
    void rejectsMissingProfileMembershipAndPaymentValues() {
        CreateMemberRequest valid = request("alice@example.com", "member password".toCharArray());

        assertThrows(IllegalArgumentException.class, () -> members.createMember(copy(valid,
                " ", valid.membershipStart(), valid.membershipExpiry(), valid.paymentAmount(), valid.paymentMethod()),
                owner.id()));
        assertThrows(IllegalArgumentException.class, () -> members.createMember(copy(valid,
                valid.fullName(), null, valid.membershipExpiry(), valid.paymentAmount(), valid.paymentMethod()),
                owner.id()));
        assertThrows(IllegalArgumentException.class, () -> members.createMember(copy(valid,
                valid.fullName(), valid.membershipStart(), valid.membershipExpiry(), BigDecimal.ZERO,
                valid.paymentMethod()), owner.id()));
        assertThrows(IllegalArgumentException.class, () -> members.createMember(copy(valid,
                valid.fullName(), valid.membershipStart(), valid.membershipExpiry(), new BigDecimal("1.001"),
                valid.paymentMethod()), owner.id()));
        assertThrows(IllegalArgumentException.class, () -> members.createMember(copy(valid,
                valid.fullName(), valid.membershipStart(), valid.membershipExpiry(), valid.paymentAmount(), null),
                owner.id()));
    }

    private static CreateMemberRequest request(String email, char[] password) {
        return request(email, password, "+65 8123 4567", LocalDate.of(1995, 3, 4));
    }

    private static CreateMemberRequest request(String email, char[] password,
            String phone, LocalDate dateOfBirth) {
        return new CreateMemberRequest(email, password, "Alice Tan", phone,
                dateOfBirth, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                new BigDecimal("120.00"), PaymentMethod.CARD, Instant.parse("2026-09-01T10:00:00Z"), "R-001");
    }

    private static CreateMemberRequest requestWithExpiry(LocalDate expiry) {
        return new CreateMemberRequest("member@example.com", "member password".toCharArray(),
                "Alice Tan", "+65 8123 4567", LocalDate.of(1995, 3, 4), LocalDate.of(2026, 9, 1), expiry,
                new BigDecimal("120.00"), PaymentMethod.CARD, Instant.parse("2026-09-01T10:00:00Z"), "R-001");
    }

    private static CreateMemberRequest copy(CreateMemberRequest request, String fullName,
            LocalDate start, LocalDate expiry, BigDecimal amount, PaymentMethod method) {
        return new CreateMemberRequest(request.email(), "member password".toCharArray(), fullName,
                request.phoneNumber(), request.dateOfBirth(), start, expiry, amount, method,
                request.paidAt(), request.paymentReference());
    }

    private int count(String table) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return results.getInt(1);
        }
    }
}
