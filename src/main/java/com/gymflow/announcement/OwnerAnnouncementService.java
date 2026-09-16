package com.gymflow.announcement;

import java.util.List;

import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.OwnerAnnouncementStore;
import com.gymflow.model.Announcement;

/** Publishes and retrieves gym-wide Owner announcements. */
public final class OwnerAnnouncementService {
    private final OwnerAnnouncementStore announcements;

    /** Creates a service backed by the supplied database. */
    public OwnerAnnouncementService(GymFlowDatabase database) {
        announcements = new OwnerAnnouncementStore(database);
    }

    /** Validates and publishes an announcement. */
    public Announcement publish(String title, String content, long ownerAccountId) {
        String normalizedTitle = required(title, "Announcement title is required");
        String normalizedContent = required(content, "Announcement content is required");
        return announcements.publish(normalizedTitle, normalizedContent, ownerAccountId);
    }

    /** Lists announcements currently visible to Members. */
    public List<Announcement> listPublished() {
        return announcements.list(false);
    }

    /** Lists withdrawn announcements retained for Owner history. */
    public List<Announcement> listWithdrawn() {
        return announcements.list(true);
    }

    /** Withdraws one published announcement. */
    public Announcement withdraw(long announcementId, long ownerAccountId) {
        return announcements.withdraw(announcementId, ownerAccountId);
    }

    private static String required(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
