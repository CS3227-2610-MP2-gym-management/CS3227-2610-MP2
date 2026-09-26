package com.gymflow.auth;

import java.util.Locale;

/** Shared validation and normalization rules for account credentials and contact details. */
public final class AccountValidation {
    private AccountValidation() {
    }

    /** Normalizes and validates an email address. */
    public static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (at <= 0 || at != normalized.lastIndexOf('@') || at == normalized.length() - 1) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        return normalized;
    }

    /** Normalizes and validates a Singapore mobile number. */
    public static String normalizePhone(String phoneNumber) {
        String compact = phoneNumber == null ? "" : phoneNumber.replaceAll("[\\s()-]", "");
        if (compact.startsWith("+65")) {
            compact = compact.substring(3);
        }
        if (!compact.matches("[3689]\\d{7}")) {
            throw new IllegalArgumentException("Enter a valid 8-digit Singapore phone number");
        }
        return "+65 %s %s".formatted(compact.substring(0, 4), compact.substring(4));
    }

    /** Validates the application's password length policy. */
    public static void validatePassword(char[] password) {
        int length = password == null ? 0 : password.length;
        if (length < 12 || length > 128) {
            throw new IllegalArgumentException("Password must be between 12 and 128 characters");
        }
    }
}
