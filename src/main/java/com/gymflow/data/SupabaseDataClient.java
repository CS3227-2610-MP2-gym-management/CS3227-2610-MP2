package com.gymflow.data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymflow.auth.AccessTokenProvider;
import com.gymflow.config.SupabaseConfiguration;

/** Small authenticated client for Supabase Data API and Edge Function requests. */
public final class SupabaseDataClient {
    private final AccessTokenProvider tokens;
    private final HttpClient client;
    private final ObjectMapper json;
    private final String publishableKey;
    private final URI url;

    /** Creates a client from public Supabase configuration and the active user session. */
    public SupabaseDataClient(SupabaseConfiguration configuration, AccessTokenProvider tokens) {
        this(configuration, tokens, HttpClient.newHttpClient(), new ObjectMapper());
    }

    SupabaseDataClient(SupabaseConfiguration configuration, AccessTokenProvider tokens,
            HttpClient client, ObjectMapper json) {
        url = configuration.url();
        publishableKey = configuration.publishableKey();
        this.tokens = tokens;
        this.client = client;
        this.json = json;
    }

    /** Performs an authenticated Data API GET and returns its JSON body. */
    public JsonNode get(String path) {
        return send(builder("/rest/v1/" + path).GET().build(), "load GymFlow data");
    }

    /** Inserts a row and returns the representation supplied by PostgREST. */
    public JsonNode post(String path, Object body) {
        return send(builder("/rest/v1/" + path)
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .POST(HttpRequest.BodyPublishers.ofString(write(body)))
                .build(), "save GymFlow data");
    }

    /** Inserts a row or updates the row selected by the requested conflict target. */
    public JsonNode upsert(String path, Object body) {
        return send(builder("/rest/v1/" + path)
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .POST(HttpRequest.BodyPublishers.ofString(write(body)))
                .build(), "save GymFlow data");
    }

    /** Updates matching rows and returns their new representations. */
    public JsonNode patch(String path, Object body) {
        return send(builder("/rest/v1/" + path)
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(write(body)))
                .build(), "update GymFlow data");
    }

    /** Deletes matching rows. */
    public JsonNode delete(String path) {
        return send(builder("/rest/v1/" + path)
                .header("Prefer", "return=representation")
                .DELETE().build(), "delete GymFlow data");
    }

    /** Calls an authenticated PostgreSQL function. */
    public JsonNode rpc(String function, Object arguments) {
        return send(builder("/rest/v1/rpc/" + function)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(write(arguments)))
                .build(), "complete the GymFlow operation");
    }

    /** Calls an authenticated Edge Function. */
    public JsonNode function(String function, Object body) {
        return send(builder("/functions/v1/" + function)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(write(body)))
                .build(), "complete the GymFlow account operation");
    }

    /** Exposes the configured mapper to repository mapping code. */
    public ObjectMapper json() {
        return json;
    }

    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(url.resolve(path))
                .header("apikey", publishableKey)
                .header("Authorization", "Bearer " + tokens.requireAccessToken())
                .header("Accept", "application/json");
    }

    private JsonNode send(HttpRequest request, String operation) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw responseFailure(response.statusCode(), response.body(), operation, json);
            }
            if (response.body() == null || response.body().isBlank()) {
                return json.createArrayNode();
            }
            return json.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to connect to GymFlow", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GymFlow operation was interrupted", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to prepare GymFlow data", exception);
        }
    }

    static RuntimeException responseFailure(int status, String body, String operation,
            ObjectMapper mapper) {
        JsonNode error = errorBody(body, mapper);
        String message = error.path("message").asText();
        if (status == 400 && "duplicate_email".equals(error.path("code").asText())
                && !message.isBlank()) {
            return new IllegalArgumentException(message);
        }
        String detail = message.isBlank() ? error.path("error_description").asText() : message;
        return new IllegalStateException("Unable to " + operation + " (HTTP " + status
                + (detail.isBlank() ? "" : ": " + detail) + ")");
    }

    private static JsonNode errorBody(String body, ObjectMapper mapper) {
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException exception) {
            return mapper.createObjectNode();
        }
    }
}
