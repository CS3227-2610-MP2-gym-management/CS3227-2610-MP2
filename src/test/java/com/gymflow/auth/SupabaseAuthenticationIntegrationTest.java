package com.gymflow.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymflow.config.RuntimeEnvironment;
import com.gymflow.config.SupabaseConfiguration;
import com.gymflow.model.Account;
import com.gymflow.model.Role;

@EnabledIfEnvironmentVariable(named = "GYMFLOW_LOCAL_INTEGRATION", matches = "true")
class SupabaseAuthenticationIntegrationTest {
    private static final String LOCAL_KEY = "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH";

    private SupabaseAuthenticationService authentication;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.now());
        authentication = new SupabaseAuthenticationService(new SupabaseConfiguration(
                RuntimeEnvironment.LOCAL, URI.create("http://127.0.0.1:54321"), LOCAL_KEY),
                HttpClient.newHttpClient(), new ObjectMapper(), clock);
    }

    @Test
    void authenticatesOwnerAndLoadsTheMappedDomainAccount() {
        char[] password = "LocalOwner!2026".toCharArray();

        Account account = authentication.authenticate("owner.local@example.test", password).orElseThrow();

        assertEquals(1, account.id());
        assertEquals(Role.OWNER, account.role());
        assertTrue(account.active());
        assertTrue(authentication.accessToken().isPresent());
        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void authenticatesMemberOnAFreshInstallation() {
        Account account = authentication.authenticate("member.b.local@example.test",
                "LocalMemberB!2026".toCharArray()).orElseThrow();

        assertEquals(3, account.id());
        assertEquals(Role.MEMBER, account.role());
    }

    @Test
    void rejectsInvalidCredentialsAndClearsThePassword() {
        char[] password = "WrongPassword!2026".toCharArray();

        Optional<Account> account = authentication.authenticate("owner.local@example.test", password);

        assertTrue(account.isEmpty());
        assertTrue(authentication.accessToken().isEmpty());
        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void signOutClearsTheLocalSession() {
        authentication.authenticate("owner.local@example.test",
                "LocalOwner!2026".toCharArray()).orElseThrow();

        authentication.signOut();

        assertFalse(authentication.accessToken().isPresent());
    }

    @Test
    void refreshesAnExpiredSession() {
        authentication.authenticate("owner.local@example.test",
                "LocalOwner!2026".toCharArray()).orElseThrow();
        String original = authentication.accessToken().orElseThrow();

        clock.advance(Duration.ofHours(2));

        String refreshed = authentication.accessToken().orElseThrow();
        assertFalse(refreshed.isBlank());
        assertFalse(original.equals(refreshed));
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
