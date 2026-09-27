package com.gymflow.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymflow.config.SupabaseConfiguration;
import com.gymflow.model.Account;
import com.gymflow.model.Role;

/** Authenticates desktop users through Supabase Auth and loads their application Account. */
public final class SupabaseAuthenticationService implements Authenticator {
    private static final String ACCOUNT_FIELDS = "id,email,role,is_active,created_at,updated_at";

    private final HttpClient client;
    private final Clock clock;
    private final ObjectMapper json;
    private final String publishableKey;
    private final URI url;
    private Session session;

    /** Creates a Supabase authenticator using the supplied public client configuration. */
    public SupabaseAuthenticationService(SupabaseConfiguration configuration) {
        this(configuration, HttpClient.newHttpClient(), new ObjectMapper(), Clock.systemUTC());
    }

    SupabaseAuthenticationService(SupabaseConfiguration configuration,
            HttpClient client, ObjectMapper json, Clock clock) {
        url = configuration.url();
        publishableKey = configuration.publishableKey();
        this.client = client;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public synchronized Optional<Account> authenticate(String email, char[] password) {
        try {
            if (email == null || email.isBlank() || password == null) {
                return Optional.empty();
            }
            HttpResponse<String> response = send(HttpRequest.newBuilder(endpoint(
                    "/auth/v1/token?grant_type=password"))
                    .header("apikey", publishableKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(credentials(email, password)))
                    .build());
            if (response.statusCode() == 400 || response.statusCode() == 401
                    || response.statusCode() == 422) {
                return Optional.empty();
            }
            requireSuccess(response, "Unable to authenticate with GymFlow");
            JsonNode payload = read(response.body());
            String accessToken = requiredText(payload, "access_token");
            String refreshToken = requiredText(payload, "refresh_token");
            long expiresIn = payload.path("expires_in").asLong();
            if (expiresIn <= 0) {
                throw new IllegalStateException("GymFlow response is missing expires_in");
            }
            String authUserId = requiredText(payload.path("user"), "id");
            Account account = loadAccount(authUserId, accessToken);
            if (!account.active()) {
                return Optional.empty();
            }
            session = new Session(accessToken, refreshToken,
                    clock.instant().plusSeconds(expiresIn), account);
            return Optional.of(account);
        } finally {
            clear(password);
        }
    }

    /** Returns the current access token for authenticated API calls. */
    public synchronized Optional<String> accessToken() {
        if (session == null) {
            return Optional.empty();
        }
        if (!session.expiresAt().isAfter(clock.instant().plusSeconds(30))) {
            refreshSession();
        }
        return Optional.of(session.accessToken());
    }

    @Override
    public synchronized void signOut() {
        Session active = session;
        session = null;
        if (active == null) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint("/auth/v1/logout"))
                .header("apikey", publishableKey)
                .header("Authorization", "Bearer " + active.accessToken())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException exception) {
            // The local session is already cleared; a network outage must not keep the user signed in.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void refreshSession() {
        Session active = session;
        if (active == null) {
            return;
        }
        String body;
        try {
            body = json.writeValueAsString(new RefreshToken(active.refreshToken()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to prepare the session refresh", exception);
        }
        HttpResponse<String> response = send(HttpRequest.newBuilder(endpoint(
                "/auth/v1/token?grant_type=refresh_token"))
                .header("apikey", publishableKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        requireSuccess(response, "Unable to refresh the GymFlow session");
        JsonNode payload = read(response.body());
        long expiresIn = payload.path("expires_in").asLong();
        if (expiresIn <= 0) {
            throw new IllegalStateException("GymFlow response is missing expires_in");
        }
        session = new Session(requiredText(payload, "access_token"),
                requiredText(payload, "refresh_token"),
                clock.instant().plusSeconds(expiresIn), active.account());
    }

    private Account loadAccount(String authUserId, String accessToken) {
        String query = "/rest/v1/accounts?auth_user_id=eq."
                + URLEncoder.encode(authUserId, StandardCharsets.UTF_8)
                + "&select=" + ACCOUNT_FIELDS + "&limit=1";
        HttpResponse<String> response = send(HttpRequest.newBuilder(endpoint(query))
                .header("apikey", publishableKey)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build());
        requireSuccess(response, "Unable to load the GymFlow account");
        JsonNode accounts = read(response.body());
        if (!accounts.isArray() || accounts.size() != 1) {
            throw new IllegalStateException("The authenticated user has no GymFlow account");
        }
        JsonNode account = accounts.get(0);
        return new Account(account.path("id").asLong(), requiredText(account, "email"),
                Role.valueOf(requiredText(account, "role")), account.path("is_active").asBoolean(),
                Instant.parse(requiredText(account, "created_at")),
                Instant.parse(requiredText(account, "updated_at")));
    }

    private String credentials(String email, char[] password) {
        try {
            return json.writeValueAsString(new Credentials(email.trim(), new String(password)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to prepare login credentials", exception);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to connect to GymFlow", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GymFlow login was interrupted", exception);
        }
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("GymFlow returned an invalid response", exception);
        }
    }

    private URI endpoint(String path) {
        return url.resolve(path);
    }

    private static String requiredText(JsonNode parent, String field) {
        String value = parent.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalStateException("GymFlow response is missing " + field);
        }
        return value;
    }

    private static void requireSuccess(HttpResponse<?> response, String message) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(message + " (HTTP " + response.statusCode() + ")");
        }
    }

    private static void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    private record Credentials(String email, String password) {
    }

    private record RefreshToken(@JsonProperty("refresh_token") String refreshToken) {
    }

    private record Session(String accessToken, String refreshToken,
            Instant expiresAt, Account account) {
    }
}
