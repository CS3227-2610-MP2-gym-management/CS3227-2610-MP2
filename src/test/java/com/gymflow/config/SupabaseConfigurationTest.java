package com.gymflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SupabaseConfigurationTest {
    @Test
    void defaultsDevelopmentToTheLocalBackend() {
        SupabaseConfiguration configuration = SupabaseConfiguration.load(Map.of());

        assertEquals(RuntimeEnvironment.LOCAL, configuration.environment());
        assertEquals(URI.create("http://127.0.0.1:54321"), configuration.url());
    }

    @Test
    void productionRequiresExplicitConfiguration() {
        assertThrows(IllegalStateException.class,
                () -> SupabaseConfiguration.load(Map.of("GYMFLOW_ENV", "production")));
    }

    @Test
    void productionRejectsLocalAndInsecureEndpoints() {
        assertThrows(IllegalArgumentException.class,
                () -> SupabaseConfiguration.load(Map.of(
                        "GYMFLOW_ENV", "production",
                        "GYMFLOW_SUPABASE_URL", "http://127.0.0.1:54321",
                        "GYMFLOW_SUPABASE_PUBLISHABLE_KEY", "publishable")));
    }

    @Test
    void productionAcceptsAnExplicitHttpsEndpoint() {
        SupabaseConfiguration configuration = SupabaseConfiguration.load(Map.of(
                "GYMFLOW_ENV", "production",
                "GYMFLOW_SUPABASE_URL", "https://gymflow.example.com",
                "GYMFLOW_SUPABASE_PUBLISHABLE_KEY", "sb_publishable_example"));

        assertEquals(RuntimeEnvironment.PRODUCTION, configuration.environment());
        assertEquals(URI.create("https://gymflow.example.com"), configuration.url());
    }

    @Test
    void localRejectsAHostedEndpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> SupabaseConfiguration.load(Map.of(
                        "GYMFLOW_ENV", "local",
                        "GYMFLOW_SUPABASE_URL", "https://gymflow.example.com",
                        "GYMFLOW_SUPABASE_PUBLISHABLE_KEY", "sb_publishable_example")));
    }

    @Test
    void productionRejectsPrivilegedKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> SupabaseConfiguration.load(Map.of(
                        "GYMFLOW_ENV", "production",
                        "GYMFLOW_SUPABASE_URL", "https://gymflow.example.com",
                        "GYMFLOW_SUPABASE_PUBLISHABLE_KEY", "sb_secret_example")));
    }

    @Test
    void productionRejectsAnEndpointWithoutAHost() {
        assertThrows(IllegalArgumentException.class,
                () -> SupabaseConfiguration.load(Map.of(
                        "GYMFLOW_ENV", "production",
                        "GYMFLOW_SUPABASE_URL", "https:gymflow",
                        "GYMFLOW_SUPABASE_PUBLISHABLE_KEY", "sb_publishable_example")));
    }

    @Test
    void acceptsOnlyLoopbackHostsForLocalConfiguration() {
        for (String host : new String[] {"localhost", "127.0.0.1", "[::1]"}) {
            SupabaseConfiguration configuration = new SupabaseConfiguration(
                    RuntimeEnvironment.LOCAL, URI.create("http://" + host + ":54321"), "key");
            assertEquals(host, configuration.url().getHost());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new SupabaseConfiguration(RuntimeEnvironment.LOCAL,
                        URI.create("https://example.com"), "key"));
    }

    @Test
    void rejectsUnknownEnvironmentAndMissingPublishableKey() {
        assertThrows(IllegalStateException.class,
                () -> SupabaseConfiguration.load(Map.of("GYMFLOW_ENV", "staging")));
        assertThrows(IllegalArgumentException.class,
                () -> new SupabaseConfiguration(RuntimeEnvironment.PRODUCTION,
                        URI.create("https://gymflow.example.com"), " "));
        assertEquals(RuntimeEnvironment.PRODUCTION,
                RuntimeEnvironment.parse(" Production "));
    }
}
