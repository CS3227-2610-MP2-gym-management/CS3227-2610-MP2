package com.gymflow.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.gymflow.announcement.OwnerAnnouncementService;
import com.gymflow.auth.SupabaseAuthenticationService;
import com.gymflow.config.RuntimeEnvironment;
import com.gymflow.config.SupabaseConfiguration;
import com.gymflow.expense.AddExpenseRequest;
import com.gymflow.expense.OwnerExpenseService;
import com.gymflow.member.AddMembershipRequest;
import com.gymflow.member.CreateMemberRequest;
import com.gymflow.member.MemberAccountService;
import com.gymflow.member.OwnerAccountService;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.metric.BodyMetricService;
import com.gymflow.model.Account;
import com.gymflow.model.ExpenseCategory;
import com.gymflow.model.PaymentMethod;
import com.gymflow.model.SaveWorkoutRequest;
import com.gymflow.model.WorkoutSetInput;
import com.gymflow.visit.MemberVisitService;
import com.gymflow.visit.OwnerVisitService;
import com.gymflow.workout.WorkoutService;

@EnabledIfEnvironmentVariable(named = "GYMFLOW_LOCAL_INTEGRATION", matches = "true")
class SupabaseFeatureIntegrationTest {
    private static final String LOCAL_KEY = "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH";

    @Test
    void ownerAndMemberFeaturesShareTheLocalCloudBackend() {
        SupabaseConfiguration configuration = new SupabaseConfiguration(RuntimeEnvironment.LOCAL,
                URI.create("http://127.0.0.1:54321"), LOCAL_KEY);
        SupabaseAuthenticationService authentication = new SupabaseAuthenticationService(configuration);
        SupabaseDataClient client = new SupabaseDataClient(configuration, authentication);
        OwnerAccountService ownerAccounts = new OwnerAccountService(client);
        OwnerMemberService members = new OwnerMemberService(client);
        OwnerExpenseService expenses = new OwnerExpenseService(client);
        OwnerAnnouncementService announcements = new OwnerAnnouncementService(client);
        OwnerVisitService ownerVisits = new OwnerVisitService(client);
        MemberAccountService memberAccounts = new MemberAccountService(client, authentication);
        MemberVisitService memberVisits = new MemberVisitService(client);
        WorkoutService workouts = new WorkoutService(client);
        BodyMetricService metrics = new BodyMetricService(client);

        Account owner = authentication.authenticate("owner.local@example.test",
                "LocalOwner!2026".toCharArray()).orElseThrow();
        char[] coOwnerPassword = "LocalCoOwner!2026".toCharArray();
        char[] currentOwnerPassword = "LocalOwner!2026".toCharArray();
        Account coOwner = ownerAccounts.createOwner("co.owner.local@example.test",
                coOwnerPassword, currentOwnerPassword);
        assertTrue(new String(coOwnerPassword).chars().allMatch(value -> value == 0));
        assertTrue(new String(currentOwnerPassword).chars().allMatch(value -> value == 0));
        assertEquals(coOwner.id(), authentication.authenticate("co.owner.local@example.test",
                "LocalCoOwner!2026".toCharArray()).orElseThrow().id());

        authentication.authenticate("owner.local@example.test",
                "LocalOwner!2026".toCharArray()).orElseThrow();
        char[] selfChangePassword = "LocalOwner!2026".toCharArray();
        assertThrows(IllegalStateException.class,
                () -> ownerAccounts.setActive(owner.id(), false, selfChangePassword));
        assertTrue(new String(selfChangePassword).chars().allMatch(value -> value == 0));
        ownerAccounts.setActive(coOwner.id(), false, "LocalOwner!2026".toCharArray());
        assertFalse(authentication.authenticate("co.owner.local@example.test",
                "LocalCoOwner!2026".toCharArray()).isPresent());
        authentication.authenticate("owner.local@example.test",
                "LocalOwner!2026".toCharArray()).orElseThrow();
        ownerAccounts.setActive(coOwner.id(), true, "LocalOwner!2026".toCharArray());
        assertEquals(2, ownerAccounts.listOwners().size());

        members.addMembership(new AddMembershipRequest(2, LocalDate.now(),
                LocalDate.now().plusDays(30), new BigDecimal("80.00"), PaymentMethod.CARD,
                Instant.now(), "LOCAL-INTEGRATION"), owner.id());
        expenses.addExpense(new AddExpenseRequest(LocalDate.now(), new BigDecimal("12.50"),
                PaymentMethod.CARD, ExpenseCategory.SUPPLIES, "Integration test"), owner.id());
        announcements.publish("Integration notice", "Visible from another login", owner.id());
        assertEquals(2, members.searchMembers("").size());
        assertEquals(0, new BigDecimal("12.50").compareTo(expenses.totalExpenses()));

        char[] initialPassword = "CloudMember!2026".toCharArray();
        var created = members.createMember(new CreateMemberRequest(
                "cloud.member.local@example.test", initialPassword, "Cloud Member",
                "+65 8000 0099", LocalDate.of(1997, 3, 3), LocalDate.now(),
                LocalDate.now().plusDays(30), new BigDecimal("90.00"), PaymentMethod.TRANSFER,
                Instant.now(), "EDGE-FUNCTION"), owner.id());
        assertEquals("Cloud Member", created.fullName());
        assertTrue(new String(initialPassword).chars().allMatch(value -> value == 0));
        var updated = members.updateMember(created.accountId(),
                "cloud.member.updated.local@example.test", "Cloud Member Updated",
                "+65 8000 0098", LocalDate.of(1997, 3, 3));
        assertEquals("Cloud Member Updated", updated.fullName());
        members.resetMemberPassword(created.accountId(),
                "CloudMemberReset!2026".toCharArray(), owner.id());
        Account createdAccount = authentication.authenticate(
                "cloud.member.updated.local@example.test",
                "CloudMemberReset!2026".toCharArray()).orElseThrow();
        assertEquals(created.accountId(), createdAccount.id());
        memberAccounts.changePassword(createdAccount, "CloudMemberReset!2026".toCharArray(),
                "CloudMemberChanged!2026".toCharArray());
        assertEquals(created.accountId(), authentication.authenticate(
                "cloud.member.updated.local@example.test",
                "CloudMemberChanged!2026".toCharArray()).orElseThrow().id());

        Account member = authentication.authenticate("member.a.local@example.test",
                "LocalMemberA!2026".toCharArray()).orElseThrow();
        assertFalse(memberAccounts.loadOverview(member).memberships().isEmpty());
        assertFalse(memberVisits.currentState(member).checkedIn());
        memberVisits.checkIn(member);
        assertTrue(memberVisits.currentState(member).checkedIn());
        memberVisits.checkOut(member);
        assertFalse(memberVisits.currentState(member).checkedIn());

        var workout = workouts.create(member, new SaveWorkoutRequest(
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800), "Cloud workout",
                List.of(new WorkoutSetInput("Squat", 8, null, new BigDecimal("50")))));
        assertEquals(1, workout.sets().size());
        assertEquals(1, workouts.history(member).size());
        workout = workouts.update(member, workout.id(), new SaveWorkoutRequest(
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1200), "Updated workout",
                List.of(new WorkoutSetInput("Plank", null, 60, null))));
        assertEquals("Plank", workout.sets().get(0).exerciseName());
        var metric = metrics.create(member, LocalDate.now(), new BigDecimal("65.500"));
        assertEquals(new BigDecimal("65.5"), metric.weightKilograms().stripTrailingZeros());
        metric = metrics.update(member, metric.id(), LocalDate.now(), new BigDecimal("65.250"));
        assertEquals(new BigDecimal("65.25"), metric.weightKilograms().stripTrailingZeros());
        assertEquals(1, announcements.listPublished().size());

        authentication.authenticate("owner.local@example.test",
                "LocalOwner!2026".toCharArray()).orElseThrow();
        assertEquals(1, ownerVisits.visitHistory(member.id()).size());
        assertEquals(3, members.searchMembers("").size());
        assertEquals(2, members.searchMemberships("").size());
        assertEquals(2, members.searchPayments("").size());
        assertEquals(3, members.ownerDashboard().totalMembers());
        assertEquals(1, expenses.listExpensesByCategory(ExpenseCategory.SUPPLIES).size());
        var published = announcements.listPublished().get(0);
        announcements.withdraw(published.id(), owner.id());
        assertEquals(1, announcements.listWithdrawn().size());
    }
}
