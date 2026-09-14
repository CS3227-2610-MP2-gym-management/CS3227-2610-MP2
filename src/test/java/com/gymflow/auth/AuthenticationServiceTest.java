package com.gymflow.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.gymflow.data.AccountStore;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthenticationServiceTest {
    @TempDir
    Path directory;
    private GymFlowDatabase database;
    private AuthenticationService authentication;

    @BeforeEach
    void setUp() {
        database = new GymFlowDatabase(directory.resolve("gymflow.db"));
        database.initialize();
        authentication = new AuthenticationService(database);
    }

    @Test
    void createsOneOwnerWithNormalizedEmailAndClearsPassword() {
        char[] password = "long enough password".toCharArray();

        Account owner = authentication.createOwner("  Owner@Example.COM ", password);

        assertEquals("owner@example.com", owner.email());
        assertTrue(authentication.hasOwner());
        assertArrayEquals(new char[password.length], password);
        assertThrows(IllegalArgumentException.class,
                () -> authentication.createOwner("second@example.com", "another long password".toCharArray()));
    }

    @Test
    void setupRejectsInvalidEmailAndPasswordLengths() {
        assertEquals("Enter a valid email address",
                assertThrows(IllegalArgumentException.class,
                        () -> authentication.createOwner("missing-at.example", "long enough password".toCharArray()))
                        .getMessage());
        assertEquals("Password must be between 12 and 128 characters",
                assertThrows(IllegalArgumentException.class,
                        () -> authentication.createOwner("owner@example.com", "too short".toCharArray()))
                        .getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> authentication.createOwner("owner@example.com", "x".repeat(129).toCharArray()));
    }

    @Test
    void authenticatesActiveOwnerUsingNormalizedEmail() {
        authentication.createOwner("owner@example.com", "correct password".toCharArray());
        char[] supplied = "correct password".toCharArray();

        Account owner = authentication.authenticate(" OWNER@EXAMPLE.COM ", supplied).orElseThrow();

        assertEquals("owner@example.com", owner.email());
        assertArrayEquals(new char[supplied.length], supplied);
    }

    @Test
    void unknownIncorrectAndInactiveAccountsAllFailAuthentication() {
        Account owner = authentication.createOwner("owner@example.com", "correct password".toCharArray());

        assertFalse(authentication.authenticate("unknown@example.com", "correct password".toCharArray()).isPresent());
        assertFalse(authentication.authenticate("owner@example.com", "incorrect password".toCharArray()).isPresent());
        new AccountStore(database).setActive(owner.id(), false);
        assertFalse(authentication.authenticate("owner@example.com", "correct password".toCharArray()).isPresent());
    }
}
