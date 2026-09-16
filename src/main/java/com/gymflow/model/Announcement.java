package com.gymflow.model;

import java.time.Instant;

/** A gym-wide notice published by an Owner. */
public record Announcement(long id, String title, String content, Instant publishedAt,
        long createdByUserId, Instant withdrawnAt, Instant createdAt, Instant updatedAt) {
}
