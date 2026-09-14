package com.gymflow.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.gymflow.auth.PasswordHash;
import com.gymflow.model.Account;
import com.gymflow.model.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountStoreTest {
    @TempDir
    Path directory;

    @Test
    void accountPersistsAfterDatabaseIsReopened() {
        Path file = directory.resolve("gymflow.db");
        PasswordHash password = new PasswordHash("hash", "salt", 600_000);

        GymFlowDatabase firstDatabase = new GymFlowDatabase(file);
        firstDatabase.initialize();
        Account created = new AccountStore(firstDatabase).create("owner@example.com", password, Role.OWNER);

        GymFlowDatabase reopenedDatabase = new GymFlowDatabase(file);
        reopenedDatabase.initialize();
        StoredAccount stored = new AccountStore(reopenedDatabase).findByEmail("OWNER@EXAMPLE.COM").orElseThrow();

        assertTrue(created.id() > 0);
        assertEquals("owner@example.com", stored.account().email());
        assertEquals(Role.OWNER, stored.account().role());
        assertEquals(password, stored.password());
    }
}
