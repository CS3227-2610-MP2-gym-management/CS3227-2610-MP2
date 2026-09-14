package com.gymflow.auth;

/** Encoded password material safe to persist. */
public record PasswordHash(String hash, String salt, int iterations) {
}
