package com.gymflow.data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymflow.model.Announcement;

/** Supabase-backed announcement operations. */
public final class SupabaseAnnouncementStore {
    private static final String FIELDS = "id,title,content,published_at,created_by_account_id,"
            + "withdrawn_at,created_at,updated_at";
    private final SupabaseDataClient client;

    /** Creates the store. */
    public SupabaseAnnouncementStore(SupabaseDataClient client) {
        this.client = client;
    }

    /** Publishes an announcement. */
    public Announcement publish(String title, String content, long ownerAccountId) {
        JsonNode rows = client.post("announcements?select=" + FIELDS,
                Map.of("title", title, "content", content, "published_at", Instant.now().toString(),
                        "created_by_account_id", ownerAccountId));
        return SupabaseRows.announcement(single(rows));
    }

    /** Lists published or withdrawn announcements. */
    public List<Announcement> list(boolean withdrawn) {
        String filter = withdrawn ? "not.is.null" : "is.null";
        JsonNode rows = client.get("announcements?select=" + FIELDS
                + "&withdrawn_at=" + filter + "&order=published_at.desc");
        return StreamSupport.stream(rows.spliterator(), false)
                .map(SupabaseRows::announcement).toList();
    }

    /** Withdraws an announcement. */
    public Announcement withdraw(long announcementId, long ownerAccountId) {
        return SupabaseRows.announcement(client.rpc("owner_withdraw_announcement",
                Map.of("p_announcement_id", announcementId)));
    }

    private static JsonNode single(JsonNode rows) {
        if (!rows.isArray() || rows.size() != 1) {
            throw new IllegalArgumentException("Announcement was not found or could not be changed");
        }
        return rows.get(0);
    }
}
