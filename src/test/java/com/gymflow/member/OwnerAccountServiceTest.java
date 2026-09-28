package com.gymflow.member;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

import com.gymflow.config.RuntimeEnvironment;
import com.gymflow.config.SupabaseConfiguration;
import com.gymflow.data.SupabaseDataClient;

class OwnerAccountServiceTest {
    private final OwnerAccountService accounts = new OwnerAccountService(new SupabaseDataClient(
            new SupabaseConfiguration(RuntimeEnvironment.LOCAL,
                    URI.create("http://127.0.0.1:54321"), "local-key"),
            () -> "unused"));

    @Test
    void invalidCreationInputsStillClearBothPasswordBuffers() {
        char[] newPassword = "short".toCharArray();
        char[] currentPassword = "current password".toCharArray();
        assertThrows(IllegalArgumentException.class,
                () -> accounts.createOwner("owner@example.com", newPassword, currentPassword));
        assertArrayEquals(new char[newPassword.length], newPassword);
        assertArrayEquals(new char[currentPassword.length], currentPassword);
    }

    @Test
    void invalidTargetStillClearsCurrentPassword() {
        char[] currentPassword = "current password".toCharArray();
        assertThrows(IllegalArgumentException.class,
                () -> accounts.setActive(0, false, currentPassword));
        assertArrayEquals(new char[currentPassword.length], currentPassword);
    }
}
