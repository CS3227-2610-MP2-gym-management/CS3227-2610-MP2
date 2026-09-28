package com.gymflow.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymflow.config.RuntimeEnvironment;
import com.gymflow.config.SupabaseConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class SupabaseAuthenticationProtocolTest {
    private static final String TOKEN = "{\"access_token\":\"access\","
            + "\"refresh_token\":\"refresh\",\"expires_in\":3600,"
            + "\"user\":{\"id\":\"auth-id\"}}";
    private HttpServer server;
    private final AtomicInteger tokenStatus = new AtomicInteger(200);
    private final AtomicReference<String> tokenBody = new AtomicReference<>(TOKEN);
    private final AtomicReference<String> accountBody = new AtomicReference<>("[]");
    private SupabaseAuthenticationService authentication;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/v1/token", exchange ->
                respond(exchange, tokenStatus.get(), tokenBody.get()));
        server.createContext("/rest/v1/accounts", exchange ->
                respond(exchange, 200, accountBody.get()));
        server.start();
        SupabaseConfiguration configuration = new SupabaseConfiguration(RuntimeEnvironment.LOCAL,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "local-key");
        authentication = new SupabaseAuthenticationService(configuration,
                HttpClient.newHttpClient(), new ObjectMapper(), Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void invalidCredentialsStatusesClearPassword() {
        for (int status : new int[] {400, 401, 422}) {
            tokenStatus.set(status);
            char[] password = "invalid password".toCharArray();
            assertTrue(authentication.authenticate("owner@example.com", password).isEmpty());
            assertArrayEquals(new char[password.length], password);
        }
    }

    @Test
    void rejectsMalformedAndIncompleteTokenResponses() {
        for (String body : new String[] {"not-json", "{}",
                "{\"access_token\":\"access\",\"refresh_token\":\"refresh\","
                        + "\"expires_in\":0,\"user\":{\"id\":\"auth-id\"}}"}) {
            tokenBody.set(body);
            char[] password = "current password".toCharArray();
            assertThrows(IllegalStateException.class,
                    () -> authentication.authenticate("owner@example.com", password));
            assertArrayEquals(new char[password.length], password);
        }
    }

    @Test
    void rejectsInvalidAccountShapeAndMultipleMappedAccounts() {
        accountBody.set("{}");
        assertThrows(IllegalStateException.class,
                () -> authentication.authenticate("owner@example.com",
                        "current password".toCharArray()));
        accountBody.set("[{},{}]");
        assertThrows(IllegalStateException.class,
                () -> authentication.authenticate("owner@example.com",
                        "current password".toCharArray()));
        accountBody.set("[]");
        assertTrue(authentication.authenticate("owner@example.com",
                "current password".toCharArray()).isEmpty());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
