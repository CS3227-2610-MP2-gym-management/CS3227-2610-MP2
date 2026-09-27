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
                "GYMFLOW_SUPABASE_PUBLISHABLE_KEY", "publishable"));

        assertEquals(RuntimeEnvironment.PRODUCTION, configuration.environment());
        assertEquals(URI.create("https://gymflow.example.com"), configuration.url());
    }
}
