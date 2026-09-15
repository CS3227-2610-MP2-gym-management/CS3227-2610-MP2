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
import java.time.ZoneId;
import java.util.List;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.model.Account;
import com.gymflow.model.OwnerDashboard;
import com.gymflow.model.Member;
import com.gymflow.model.MemberPayment;
import com.gymflow.model.Membership;
import com.gymflow.model.MembershipOverview;
import com.gymflow.model.MembershipStatus;
import com.gymflow.model.PaymentMethod;
import com.gymflow.model.PaymentOverview;
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
    void ownerResetsMemberPasswordWithoutChangingMemberRecords() throws Exception {
        Member member = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());
        String originalHash = accountValue(member.accountId(), "password_hash");
        String originalSalt = accountValue(member.accountId(), "password_salt");
        int membershipsBefore = count("memberships");
        int paymentsBefore = count("payments");
        char[] replacement = "replacement password".toCharArray();

        members.resetMemberPassword(member.accountId(), replacement, owner.id());

        assertArrayEquals(new char[replacement.length], replacement);
        assertFalse(authentication.authenticate(
                member.email(), "member password".toCharArray()).isPresent());
        assertTrue(authentication.authenticate(
                member.email(), "replacement password".toCharArray()).isPresent());
        assertFalse(originalHash.equals(accountValue(member.accountId(), "password_hash")));
        assertFalse(originalSalt.equals(accountValue(member.accountId(), "password_salt")));
        assertEquals(membershipsBefore, count("memberships"));
        assertEquals(paymentsBefore, count("payments"));
        assertEquals("1", accountValue(member.accountId(), "is_active"));
    }

    @Test
    void rejectsInvalidMemberPasswordsAndClearsThem() {
        Member member = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());
        char[] shortPassword = "too short".toCharArray();
        char[] longPassword = new char[129];

        assertThrows(IllegalArgumentException.class,
                () -> members.resetMemberPassword(member.accountId(), shortPassword, owner.id()));
        assertThrows(IllegalArgumentException.class,
                () -> members.resetMemberPassword(member.accountId(), longPassword, owner.id()));

        assertArrayEquals(new char[shortPassword.length], shortPassword);
        assertArrayEquals(new char[longPassword.length], longPassword);
        assertTrue(authentication.authenticate(
                member.email(), "member password".toCharArray()).isPresent());
    }

    @Test
    void rejectsPasswordResetWithoutAnActiveOwnerOrMemberTarget() throws Exception {
        Member member = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());
        char[] ownerTargetPassword = "replacement password".toCharArray();
        char[] missingMemberPassword = "replacement password".toCharArray();

        assertThrows(IllegalArgumentException.class,
                () -> members.resetMemberPassword(owner.id(), ownerTargetPassword, owner.id()));
        assertThrows(IllegalArgumentException.class,
                () -> members.resetMemberPassword(999, missingMemberPassword, owner.id()));
        assertArrayEquals(new char[ownerTargetPassword.length], ownerTargetPassword);
        assertArrayEquals(new char[missingMemberPassword.length], missingMemberPassword);

        execute("UPDATE accounts SET is_active = 0 WHERE id = " + owner.id());
        char[] unauthorizedPassword = "replacement password".toCharArray();
        assertThrows(IllegalArgumentException.class,
                () -> members.resetMemberPassword(member.accountId(), unauthorizedPassword, owner.id()));
        assertArrayEquals(new char[unauthorizedPassword.length], unauthorizedPassword);
        assertTrue(authentication.authenticate(
                member.email(), "member password".toCharArray()).isPresent());
    }

    @Test
    void resetsInactiveMemberPasswordWithoutReactivatingAccount() throws Exception {
        Member member = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());
        execute("UPDATE accounts SET is_active = 0 WHERE id = " + member.accountId());
        String originalHash = accountValue(member.accountId(), "password_hash");

        members.resetMemberPassword(member.accountId(),
                "replacement password".toCharArray(), owner.id());

        assertFalse(originalHash.equals(accountValue(member.accountId(), "password_hash")));
        assertEquals("0", accountValue(member.accountId(), "is_active"));
        assertFalse(authentication.authenticate(
                member.email(), "replacement password".toCharArray()).isPresent());
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
        assertEquals(member.accountId(), members.membershipHistory(member.accountId()).getFirst().memberId());
        assertEquals(owner.id(), payment.recordedByAccountId());
    }

    @Test
    void addsMembershipAndPaymentAtomicallyAndDerivesStatus() throws Exception {
        Member member = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());
        AddMembershipRequest renewal = membershipRequest(member.accountId(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        Membership created = members.addMembership(renewal, owner.id());
        MemberPayment payment = members.paymentHistory(member.accountId()).getFirst();

        assertEquals(MembershipStatus.UPCOMING, created.status(LocalDate.of(2026, 9, 14)));
        assertEquals(created.createdAt(), created.updatedAt());
        assertEquals(created.id(), payment.membershipId());
        assertEquals(owner.id(), payment.recordedByAccountId());
        assertTrue(payment.createdAt() != null);
        assertEquals(2, members.membershipHistory(member.accountId()).size());
        assertEquals(2, members.paymentHistory(member.accountId()).size());
        assertEquals(2, count("memberships"));
        assertEquals(2, count("payments"));
    }

    @Test
    void membershipValidityUsesInclusiveDatesAndActiveFlag() {
        Member member = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());

        assertTrue(members.hasValidMembership(member.accountId(), LocalDate.of(2026, 9, 1)));
        assertTrue(members.hasValidMembership(member.accountId(), LocalDate.of(2026, 9, 30)));
        assertFalse(members.hasValidMembership(member.accountId(), LocalDate.of(2026, 10, 1)));

        Membership membership = members.membershipHistory(member.accountId()).getFirst();
        members.setMembershipActive(membership.id(), false, owner.id());

        assertFalse(members.hasValidMembership(member.accountId(), LocalDate.of(2026, 9, 14)));
        assertTrue(authentication.authenticate("alice@example.com", "member password".toCharArray()).isPresent());
    }

    @Test
    void rejectsActiveOverlapButAllowsReplacementOfDeactivatedPeriod() {
        Member member = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());
        AddMembershipRequest overlapping = membershipRequest(member.accountId(),
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15));

        assertThrows(IllegalArgumentException.class, () -> members.addMembership(overlapping, owner.id()));

        Membership initial = members.membershipHistory(member.accountId()).getFirst();
        members.setMembershipActive(initial.id(), false, owner.id());
        Membership replacement = members.addMembership(overlapping, owner.id());

        assertTrue(replacement.active());
    }

    @Test
    void rejectsExpiredOrOverlappingReactivation() {
        Member member = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());
        Membership initial = members.membershipHistory(member.accountId()).getFirst();
        members.setMembershipActive(initial.id(), false, owner.id());
        members.addMembership(membershipRequest(member.accountId(),
                initial.startDate(), initial.expiryDate()), owner.id());

        assertThrows(IllegalArgumentException.class,
                () -> members.setMembershipActive(initial.id(), true, owner.id()));

        CreateMemberRequest expiredRequest = new CreateMemberRequest("expired@example.com",
                "member password".toCharArray(), "Expired Member", "81234567", null,
                LocalDate.now().minusMonths(2), LocalDate.now().minusMonths(1),
                new BigDecimal("50.00"), PaymentMethod.CASH, Instant.now(), "");
        Member expiredMember = members.createMember(expiredRequest, owner.id());
        Membership expired = members.membershipHistory(expiredMember.accountId()).getFirst();
        members.setMembershipActive(expired.id(), false, owner.id());

        assertThrows(IllegalArgumentException.class,
                () -> members.setMembershipActive(expired.id(), true, owner.id()));
    }

    @Test
    void reactivatesValidMembershipAndUpdatesOnlyItsAccessState() {
        LocalDate today = LocalDate.now();
        CreateMemberRequest request = new CreateMemberRequest("alice@example.com",
                "member password".toCharArray(), "Alice Tan", "+65 8123 4567",
                LocalDate.of(1995, 3, 4), today, today.plusMonths(1),
                new BigDecimal("120.00"), PaymentMethod.CARD, Instant.now(), "R-001");
        Member member = members.createMember(request, owner.id());
        Membership initial = members.membershipHistory(member.accountId()).getFirst();
        Membership deactivated = members.setMembershipActive(initial.id(), false, owner.id());

        Membership reactivated = members.setMembershipActive(initial.id(), true, owner.id());

        assertTrue(reactivated.active());
        assertEquals(initial.createdAt(), reactivated.createdAt());
        assertFalse(reactivated.updatedAt().isBefore(deactivated.updatedAt()));
        assertTrue(members.hasValidMembership(member.accountId(), today));
        assertTrue(authentication.authenticate(
                "alice@example.com", "member password".toCharArray()).isPresent());
    }

    @Test
    void rejectsInvalidMembershipInputsAndUnknownActors() {
        Member member = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());
        AddMembershipRequest valid = membershipRequest(member.accountId(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThrows(IllegalArgumentException.class, () -> members.addMembership(
                new AddMembershipRequest(member.accountId(), valid.expiryDate(), valid.startDate(),
                        valid.paymentAmount(), valid.paymentMethod(), valid.paidAt(), ""), owner.id()));
        assertThrows(IllegalArgumentException.class, () -> members.addMembership(
                new AddMembershipRequest(member.accountId(), valid.startDate(), valid.expiryDate(),
                        BigDecimal.ZERO, valid.paymentMethod(), valid.paidAt(), ""), owner.id()));
        assertThrows(IllegalArgumentException.class, () -> members.addMembership(valid, 999));
        assertThrows(IllegalArgumentException.class, () -> members.addMembership(
                membershipRequest(999, valid.startDate(), valid.expiryDate()), owner.id()));
    }

    @Test
    void failedPaymentInsertRollsBackAddedMembership() throws Exception {
        Member member = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TRIGGER reject_renewal_payment BEFORE INSERT ON payments
                    BEGIN SELECT RAISE(ABORT, 'reject payment'); END
                    """);
        }

        assertThrows(IllegalStateException.class, () -> members.addMembership(
                membershipRequest(member.accountId(), LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 31)), owner.id()));

        assertEquals(1, count("memberships"));
        assertEquals(1, count("payments"));
    }

    @Test
    void searchesMembershipsByMemberNameOrEmail() {
        members.createMember(request("alice@example.com", "member password".toCharArray()), owner.id());
        Member bob = members.createMember(
                request("bob@example.com", "another password".toCharArray()), owner.id());
        members.updateMember(bob.accountId(), bob.email(), "Bob Lee",
                bob.phoneNumber(), bob.dateOfBirth());

        MembershipOverview alice = members.searchMemberships("ALICE").getFirst();

        assertEquals("Alice Tan", alice.memberName());
        assertEquals("alice@example.com", alice.memberEmail());
        assertEquals(1, members.searchMemberships("bob@").size());
        assertTrue(members.searchMemberships("M000001").isEmpty());
    }

    @Test
    void searchesGlobalPaymentsWithMemberAndMembershipDetails() {
        Member alice = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());
        Member bob = members.createMember(
                request("bob@example.com", "another password".toCharArray()), owner.id());
        members.updateMember(bob.accountId(), bob.email(), "Bob Lee",
                bob.phoneNumber(), bob.dateOfBirth());
        members.addMembership(new AddMembershipRequest(alice.accountId(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                new BigDecimal("90.00"), PaymentMethod.CASH,
                Instant.parse("2026-09-15T12:00:00Z"), "LATEST"), owner.id());

        List<PaymentOverview> all = members.searchPayments("");

        assertEquals(3, all.size());
        assertEquals(List.of("Alice Tan", "Bob Lee", "Alice Tan"),
                all.stream().map(PaymentOverview::memberName).toList());
        assertEquals("LATEST", all.getFirst().payment().reference());
        assertEquals("Alice Tan", all.getFirst().memberName());
        assertEquals("alice@example.com", all.getFirst().memberEmail());
        assertEquals(LocalDate.of(2026, 10, 1), all.getFirst().membershipStart());
        assertEquals(LocalDate.of(2026, 10, 31), all.getFirst().membershipExpiry());
        assertEquals(2, members.searchPayments("ALICE").size());
        assertEquals(1, members.searchPayments("bob@").size());
        assertTrue(members.searchPayments(alice.memberNumber()).isEmpty());
        assertTrue(members.searchPayments("LATEST").isEmpty());
        assertTrue(members.searchPayments("CARD").isEmpty());
    }

    @Test
    void summarizesCurrentMonthAndFiveMostRecentMembers() throws Exception {
        LocalDate today = LocalDate.now();
        Instant thisMonth = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        for (int index = 1; index <= 6; index++) {
            CreateMemberRequest request = new CreateMemberRequest("member" + index + "@example.com",
                    "member password".toCharArray(), "Member " + index, "81234567", null,
                    today, today.plusMonths(1), BigDecimal.valueOf(index * 10L),
                    PaymentMethod.CARD, thisMonth.plusSeconds(index), "");
            members.createMember(request, owner.id());
        }
        execute("UPDATE payments SET paid_at = '2020-01-01T00:00:00Z' WHERE id = 1");
        Membership deactivated = members.membershipHistory(
                members.searchMembers("member2@").getFirst().accountId()).getFirst();
        members.setMembershipActive(deactivated.id(), false, owner.id());

        OwnerDashboard dashboard = members.ownerDashboard();

        assertEquals(6, dashboard.totalMembers());
        assertEquals(5, dashboard.activeMemberships());
        assertEquals(new BigDecimal("200.00"), dashboard.revenueThisMonth());
        assertEquals(List.of("Member 6", "Member 5", "Member 4", "Member 3", "Member 2"),
                dashboard.members().stream().map(MembershipOverview::memberName).toList());
        assertEquals(MembershipStatus.DEACTIVATED,
                dashboard.members().getLast().membership().status(today));
    }

    @Test
    void searchesByNameAndEmailOnlyThenEditsProfileWithoutChangingMemberNumber() {
        Member created = members.createMember(
                request("alice@example.com", "member password".toCharArray()), owner.id());
        String originalUpdate = accountValue(created.accountId(), "updated_at");

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
        assertFalse(originalUpdate.equals(accountValue(created.accountId(), "updated_at")));
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

    private static AddMembershipRequest membershipRequest(long memberId,
            LocalDate start, LocalDate expiry) {
        return new AddMembershipRequest(memberId, start, expiry, new BigDecimal("90.00"),
                PaymentMethod.TRANSFER, Instant.parse("2026-09-14T12:00:00Z"), "R-002");
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

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private String accountValue(long accountId, String column) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery(
                        "SELECT " + column + " FROM accounts WHERE id = " + accountId)) {
            return results.getString(1);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
