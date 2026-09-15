package com.gymflow.model;

import java.time.Instant;

/** One completed or ongoing visit to the gym. */
public record Visit(long id, long memberId, Instant enteredAt,
        Instant exitedAt, Instant createdAt, Instant correctedAt,
        Long correctedByUserId, String correctionReason) {
}
