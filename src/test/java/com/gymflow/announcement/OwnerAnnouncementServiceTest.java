package com.gymflow.announcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;

import com.gymflow.auth.AuthenticationService;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.model.Account;
import com.gymflow.model.Announcement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnerAnnouncementServiceTest {
    @TempDir
    Path directory;
    private Path databaseFile;
    private OwnerAnnouncementService announcements;
    private Account owner;

    @BeforeEach
    void setUp() {
        databaseFile = directory.resolve("gymflow.db");
        GymFlowDatabase database = new GymFlowDatabase(databaseFile);
        database.initialize();
        owner = new AuthenticationService(database).createOwner(
                "owner@example.com", "owner password".toCharArray());
        announcements = new OwnerAnnouncementService(database);
    }

    @Test
    void publishesTrimmedAnnouncementForActiveOwner() {
        Announcement created = announcements.publish(
                "  Holiday hours  ", "  We close at 6 pm.  ", owner.id());

        assertEquals("Holiday hours", created.title());
        assertEquals("We close at 6 pm.", created.content());
        assertEquals(owner.id(), created.createdByUserId());
        assertNotNull(created.publishedAt());
        assertEquals(created.publishedAt(), created.createdAt());
        assertEquals(created.createdAt(), created.updatedAt());
        assertNull(created.withdrawnAt());
    }

    @Test
    void rejectsBlankContentAndUnauthorizedAccounts() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> announcements.publish(" ", "Content", owner.id()));
        assertThrows(IllegalArgumentException.class,
                () -> announcements.publish("Title", " ", owner.id()));
        assertThrows(IllegalArgumentException.class,
                () -> announcements.publish("Title", "Content", 999));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
                var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE accounts SET is_active = 0 WHERE id = " + owner.id());
        }
        assertThrows(IllegalArgumentException.class,
                () -> announcements.publish("Title", "Content", owner.id()));
        assertEquals(List.of(), announcements.listPublished());
    }

    @Test
    void withdrawsWithoutDeletingHistoryAndRejectsRepeatWithdrawal() {
        Announcement first = announcements.publish("First", "Oldest", owner.id());
        Announcement second = announcements.publish("Second", "Newest", owner.id());

        assertEquals(List.of(second.id(), first.id()), announcements.listPublished().stream()
                .map(Announcement::id).toList());

        Announcement withdrawn = announcements.withdraw(second.id(), owner.id());

        assertNotNull(withdrawn.withdrawnAt());
        assertEquals(withdrawn.withdrawnAt(), withdrawn.updatedAt());
        assertEquals(second.title(), withdrawn.title());
        assertEquals(second.content(), withdrawn.content());
        assertEquals(second.createdByUserId(), withdrawn.createdByUserId());
        assertEquals(second.publishedAt(), withdrawn.publishedAt());
        assertEquals(second.createdAt(), withdrawn.createdAt());
        assertEquals(List.of(first.id()), announcements.listPublished().stream()
                .map(Announcement::id).toList());
        assertEquals(List.of(second.id()), announcements.listWithdrawn().stream()
                .map(Announcement::id).toList());
        assertThrows(IllegalArgumentException.class,
                () -> announcements.withdraw(second.id(), owner.id()));
    }

    @Test
    void rejectsWithdrawalByMissingOrInactiveOwnerWithoutChangingAnnouncement() throws Exception {
        Announcement published = announcements.publish("Notice", "Still visible", owner.id());

        assertThrows(IllegalArgumentException.class,
                () -> announcements.withdraw(published.id(), 999));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
                var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE accounts SET is_active = 0 WHERE id = " + owner.id());
        }
        assertThrows(IllegalArgumentException.class,
                () -> announcements.withdraw(published.id(), owner.id()));
        assertEquals(List.of(published.id()), announcements.listPublished().stream()
                .map(Announcement::id).toList());
        assertEquals(List.of(), announcements.listWithdrawn());
    }
}
