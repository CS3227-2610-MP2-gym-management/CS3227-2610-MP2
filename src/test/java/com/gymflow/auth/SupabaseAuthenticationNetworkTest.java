package com.gymflow.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;

import org.junit.jupiter.api.Test;

import com.gymflow.config.RuntimeEnvironment;
import com.gymflow.config.SupabaseConfiguration;

class SupabaseAuthenticationNetworkTest {
    @Test
    void reportsAnUnavailableBackendSeparatelyFromInvalidCredentials() throws IOException {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }
        SupabaseConfiguration configuration = new SupabaseConfiguration(RuntimeEnvironment.LOCAL,
                URI.create("http://127.0.0.1:" + unavailablePort),
                "sb_publishable_network_test");
        SupabaseAuthenticationService authentication =
                new SupabaseAuthenticationService(configuration);
        char[] password = "DisposablePassword!2026".toCharArray();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> authentication.authenticate("offline@example.test", password));

        assertEquals("Unable to connect to GymFlow", failure.getMessage());
        assertTrue(new String(password).chars().allMatch(value -> value == 0));
    }
}
