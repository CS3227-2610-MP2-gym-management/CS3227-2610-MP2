package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.gymflow.model.Account;
import com.gymflow.model.Role;

class AppViewAuthorizationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-16T00:00:00Z");

    @Test
    void allowsOnlyAnOwnerSessionToOpenOwnerScreens() {
        Account owner = new Account(1, "owner@example.com", Role.OWNER, true, CREATED_AT, CREATED_AT);
        Account member = new Account(2, "member@example.com", Role.MEMBER, true, CREATED_AT, CREATED_AT);

        assertTrue(AppView.isOwnerSession(owner));
        assertFalse(AppView.isOwnerSession(member));
        assertFalse(AppView.isOwnerSession(null));
    }
}
