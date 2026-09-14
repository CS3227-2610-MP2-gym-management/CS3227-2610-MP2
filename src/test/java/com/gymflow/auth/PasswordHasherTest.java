package com.gymflow.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashingTheSamePasswordTwiceUsesDifferentSalts() {
        char[] password = "correct horse battery staple".toCharArray();

        PasswordHash first = hasher.hash(password);
        PasswordHash second = hasher.hash(password);

        assertNotEquals(first.salt(), second.salt());
        assertNotEquals(first.hash(), second.hash());
    }

    @Test
    void verificationAcceptsOnlyTheExactPassword() {
        PasswordHash stored = hasher.hash("  pāssword 🔒  ".toCharArray());

        assertTrue(hasher.verify("  pāssword 🔒  ".toCharArray(), stored));
        assertFalse(hasher.verify("pāssword 🔒".toCharArray(), stored));
        assertFalse(hasher.verify("incorrect password".toCharArray(), stored));
    }
}
