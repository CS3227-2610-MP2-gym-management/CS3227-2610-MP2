package com.gymflow.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.gymflow.auth.SupabaseAuthenticationService;
import com.gymflow.config.RuntimeEnvironment;
import com.gymflow.config.SupabaseConfiguration;
import com.gymflow.member.CreateMemberRequest;
import com.gymflow.member.OwnerMemberService;
import com.gymflow.model.PaymentMethod;
import com.gymflow.visit.MemberVisitService;

@EnabledIfEnvironmentVariable(named = "GYMFLOW_LOCAL_INTEGRATION", matches = "true")
class SupabaseConcurrencyIntegrationTest {
    private static final String LOCAL_KEY = "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH";

    @Test
    void simultaneousOnboardingKeepsOneAccountAndPayment() throws Exception {
        SupabaseConfiguration configuration = configuration();
        SupabaseAuthenticationService authentication = new SupabaseAuthenticationService(configuration);
        SupabaseDataClient client = new SupabaseDataClient(configuration, authentication);
        long ownerId = authentication.authenticate("owner.local@example.test",
                "LocalOwner!2026".toCharArray()).orElseThrow().id();
        OwnerMemberService members = new OwnerMemberService(client);
        String email = UUID.randomUUID() + "@example.test";
        int before = members.searchPayments(email).size();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> create = () -> {
                ready.countDown();
                start.await();
                try {
                    members.createMember(request(email), ownerId);
                    return true;
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(create);
            Future<Boolean> second = executor.submit(create);
            ready.await();
            start.countDown();
            assertEquals(1, successes(first, second));
        }

        assertEquals(1, members.searchMembers(email).size());
        assertEquals(before + 1, members.searchPayments(email).size());
    }

    @Test
    void simultaneousCheckInCreatesOneOpenSession() throws Exception {
        SupabaseConfiguration configuration = configuration();
        SupabaseAuthenticationService authentication = new SupabaseAuthenticationService(configuration);
        SupabaseDataClient client = new SupabaseDataClient(configuration, authentication);
        long ownerId = authentication.authenticate("owner.local@example.test",
                "LocalOwner!2026".toCharArray()).orElseThrow().id();
        String email = UUID.randomUUID() + "@example.test";
        new OwnerMemberService(client).createMember(request(email), ownerId);
        var member = authentication.authenticate(email, "ConcurrentMember!2026".toCharArray())
                .orElseThrow();
        MemberVisitService visits = new MemberVisitService(client);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> checkIn = () -> {
                ready.countDown();
                start.await();
                try {
                    visits.checkIn(member);
                    return true;
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(checkIn);
            Future<Boolean> second = executor.submit(checkIn);
            ready.await();
            start.countDown();
            assertEquals(1, successes(first, second));
        }
        assertTrue(visits.currentState(member).checkedIn());
        assertEquals(1, new SupabaseVisitStore(client).history(member.id()).size());
    }

    private static int successes(Future<Boolean> first, Future<Boolean> second)
            throws InterruptedException, ExecutionException {
        return (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
    }

    private static CreateMemberRequest request(String email) {
        return new CreateMemberRequest(email, "ConcurrentMember!2026".toCharArray(),
                "Concurrent Member", "+65 8000 0097", null,
                LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("50.00"),
                PaymentMethod.CARD, Instant.now(), null);
    }

    private static SupabaseConfiguration configuration() {
        return new SupabaseConfiguration(RuntimeEnvironment.LOCAL,
                URI.create("http://127.0.0.1:54321"), LOCAL_KEY);
    }
}
