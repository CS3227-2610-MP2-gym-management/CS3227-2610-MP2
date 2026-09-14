package com.gymflow.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.gymflow.data.GymFlowDatabase;
import com.gymflow.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthenticationResetTest {
    @TempDir
    Path directory;
    private AuthenticationService authentication;
    private Account owner;

    @BeforeEach
    void setUp() {
        GymFlowDatabase database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        database.initialize();
        authentication = new AuthenticationService(database);
        owner = authentication.createOwner("owner@example.com", "correct password".toCharArray());
    }

    @Test
    void incorrectResetGuardsLeaveOwnerUntouched() {
        assertThrows(IllegalArgumentException.class,
                () -> authentication.resetAll(owner, "wrong password".toCharArray(), "RESET"));
        assertTrue(authentication.hasOwner());

        assertThrows(IllegalArgumentException.class,
                () -> authentication.resetAll(owner, "correct password".toCharArray(), "reset"));
        assertTrue(authentication.hasOwner());
    }

    @Test
    void successfulResetClearsOwnerAndAllowsFreshSetup() {
        char[] password = "correct password".toCharArray();

        authentication.resetAll(owner, password, "RESET");

        assertArrayEquals(new char[password.length], password);
        assertFalse(authentication.hasOwner());
        assertTrue(authentication.createOwner("new@example.com", "new owner password".toCharArray()).id() > 0);
    }
}
