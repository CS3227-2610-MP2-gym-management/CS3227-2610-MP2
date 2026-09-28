package com.gymflow.config;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** Non-secret client configuration for a local or hosted Supabase project. */
public record SupabaseConfiguration(RuntimeEnvironment environment, URI url,
        String publishableKey) {
    private static final String LOCAL_URL = "http://127.0.0.1:54321";
    private static final String LOCAL_PUBLISHABLE_KEY = "sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH";

    /** Validates explicitly supplied Supabase client configuration. */
    public SupabaseConfiguration {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(url);
        if (publishableKey == null || publishableKey.isBlank()) {
            throw new IllegalArgumentException("A Supabase publishable key is required");
        }
        String host = url.getHost();
        if (environment == RuntimeEnvironment.LOCAL) {
            if (!isLoopbackHost(host)) {
                throw new IllegalArgumentException("Local Supabase must use a loopback endpoint");
            }
        } else {
            if (!"https".equalsIgnoreCase(url.getScheme())) {
                throw new IllegalArgumentException("Production Supabase must use HTTPS");
            }
            if (host == null || isLoopbackHost(host)) {
                throw new IllegalArgumentException("Production Supabase cannot use a local endpoint");
            }
            if (!publishableKey.startsWith("sb_publishable_")) {
                throw new IllegalArgumentException(
                        "Production requires a Supabase publishable key");
            }
        }
    }

    /** Loads configuration from environment variables, defaulting development to local Supabase. */
    public static SupabaseConfiguration load() {
        return load(System.getenv());
    }

    static SupabaseConfiguration load(Map<String, String> variables) {
        RuntimeEnvironment environment = RuntimeEnvironment.parse(variables.get("GYMFLOW_ENV"));
        String url = variables.get("GYMFLOW_SUPABASE_URL");
        String key = variables.get("GYMFLOW_SUPABASE_PUBLISHABLE_KEY");
        if (environment == RuntimeEnvironment.LOCAL) {
            url = defaultIfBlank(url, LOCAL_URL);
            key = defaultIfBlank(key, LOCAL_PUBLISHABLE_KEY);
        } else if (url == null || url.isBlank() || key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Production requires GYMFLOW_SUPABASE_URL and GYMFLOW_SUPABASE_PUBLISHABLE_KEY");
        }
        return new SupabaseConfiguration(environment, URI.create(url), key);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isLoopbackHost(String host) {
        return host != null && ("localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host));
    }
}
