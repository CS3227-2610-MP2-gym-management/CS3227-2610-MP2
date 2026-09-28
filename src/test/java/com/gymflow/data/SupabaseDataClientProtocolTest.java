package com.gymflow.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymflow.config.RuntimeEnvironment;
import com.gymflow.config.SupabaseConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class SupabaseDataClientProtocolTest {
    private HttpServer server;
    private SupabaseDataClient client;
    private final List<Request> requests = new ArrayList<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>("[]");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        var configuration = new SupabaseConfiguration(RuntimeEnvironment.LOCAL,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-key");
        client = new SupabaseDataClient(configuration, () -> "user-token",
                HttpClient.newHttpClient(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsEachOperationToTheExpectedAuthenticatedEndpoint() {
        client.get("members?select=id");
        client.post("members", Map.of("name", "A"));
        client.upsert("members?on_conflict=id", Map.of("id", 1));
        client.patch("members?id=eq.1", Map.of("name", "B"));
        client.delete("members?id=eq.1");
        client.rpc("save_member", Map.of("id", 1));
        client.function("manage-member", Map.of("action", "update"));

        assertEquals(List.of("GET", "POST", "POST", "PATCH", "DELETE", "POST", "POST"),
                requests.stream().map(Request::method).toList());
        assertEquals("/rest/v1/members?select=id", requests.get(0).path());
        assertEquals("/rest/v1/rpc/save_member", requests.get(5).path());
        assertEquals("/functions/v1/manage-member", requests.get(6).path());
        assertTrue(requests.stream().allMatch(request ->
                "Bearer user-token".equals(request.authorization())));
        assertTrue(requests.stream().allMatch(request ->
                "test-key".equals(request.key())));
        assertTrue(requests.get(2).prefer().contains("resolution=merge-duplicates"));
        assertTrue(requests.get(1).body().contains("\"name\":\"A\""));
    }

    @Test
    void emptyResponseMeansNoRowsAndMalformedJsonIsAProtocolError() {
        responseBody.set(" ");
        assertTrue(client.get("members").isEmpty());
        responseBody.set("not-json");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> client.get("members"));
        assertEquals("GymFlow returned an invalid response", failure.getMessage());
    }

    private void respond(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        synchronized (requests) {
            requests.add(new Request(exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("apikey"),
                    exchange.getRequestHeaders().getFirst("Prefer"), body));
        }
        byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private record Request(String method, String path, String authorization,
            String key, String prefer, String body) {
    }
}
