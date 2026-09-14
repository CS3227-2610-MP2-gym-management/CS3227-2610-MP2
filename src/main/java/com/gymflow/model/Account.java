package com.gymflow.model;

import java.time.Instant;

/** Non-secret account details used by the application. */
public record Account(long id, String email, Role role, boolean active, Instant createdAt) {
}
