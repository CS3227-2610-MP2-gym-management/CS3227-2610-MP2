package com.gymflow.announcement;

import java.util.List;

import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.OwnerAnnouncementStore;
import com.gymflow.data.SupabaseAnnouncementStore;
import com.gymflow.data.SupabaseDataClient;
import com.gymflow.model.Announcement;

/** Publishes and retrieves gym-wide Owner announcements. */
public final class OwnerAnnouncementService {
    private final OwnerAnnouncementStore announcements;
    private final SupabaseAnnouncementStore cloudAnnouncements;

    /** Creates a service backed by the supplied database. */
    public OwnerAnnouncementService(GymFlowDatabase database) {
        announcements = new OwnerAnnouncementStore(database);
        cloudAnnouncements = null;
    }

    /** Creates a service backed by Supabase. */
    public OwnerAnnouncementService(SupabaseDataClient client) {
        announcements = null;
        cloudAnnouncements = new SupabaseAnnouncementStore(client);
    }

    /** Validates and publishes an announcement. */
    public Announcement publish(String title, String content, long ownerAccountId) {
        String normalizedTitle = required(title, "Announcement title is required");
        String normalizedContent = required(content, "Announcement content is required");
        return cloudAnnouncements == null
                ? announcements.publish(normalizedTitle, normalizedContent, ownerAccountId)
                : cloudAnnouncements.publish(normalizedTitle, normalizedContent, ownerAccountId);
    }

    /** Lists announcements currently visible to Members. */
    public List<Announcement> listPublished() {
        return cloudAnnouncements == null ? announcements.list(false) : cloudAnnouncements.list(false);
    }

    /** Lists withdrawn announcements retained for Owner history. */
    public List<Announcement> listWithdrawn() {
        return cloudAnnouncements == null ? announcements.list(true) : cloudAnnouncements.list(true);
    }

    /** Withdraws one published announcement. */
    public Announcement withdraw(long announcementId, long ownerAccountId) {
        return cloudAnnouncements == null
                ? announcements.withdraw(announcementId, ownerAccountId)
                : cloudAnnouncements.withdraw(announcementId, ownerAccountId);
    }

    private static String required(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
