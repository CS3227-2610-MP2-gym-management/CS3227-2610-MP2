package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.gymflow.model.Account;
import com.gymflow.model.Role;

class OwnerAccountsViewTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-29T00:00:00Z");

    @Test
    void neverAllowsChangingRootOwnerActiveState() {
        Account session = owner(2);
        Account root = owner(1);

        assertFalse(OwnerAccountsView.canSetActive(root, session, root.id()));
    }

    @Test
    void doesNotAllowOwnerToChangeOwnActiveState() {
        Account session = owner(2);

        assertFalse(OwnerAccountsView.canSetActive(session, session, 1));
    }

    @Test
    void allowsOwnerToChangeAnotherCoOwnerActiveState() {
        Account session = owner(2);
        Account coOwner = owner(3);

        assertTrue(OwnerAccountsView.canSetActive(coOwner, session, 1));
    }

    @Test
    void labelsRootAndCoOwnersWithoutGymAdministratorTerminology() {
        Account root = owner(1);
        Account session = owner(2);
        Account coOwner = owner(3);

        assertEquals("Root Owner", OwnerAccountsView.ownerLabel(root, session, root.id()));
        assertEquals("Current signed-in Owner",
                OwnerAccountsView.ownerLabel(session, session, root.id()));
        assertEquals("Owner", OwnerAccountsView.ownerLabel(coOwner, session, root.id()));
        assertEquals("Current signed-in Root Owner",
                OwnerAccountsView.ownerLabel(root, root, root.id()));
    }

    private static Account owner(long id) {
        return new Account(id, "owner" + id + "@example.com", Role.OWNER, true,
                CREATED_AT, CREATED_AT);
    }
}
