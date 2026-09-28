package com.gymflow.member;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymflow.auth.AccountValidation;
import com.gymflow.data.SupabaseDataClient;
import com.gymflow.model.Account;
import com.gymflow.model.Role;

/** Owner-only account administration backed by protected server operations. */
public final class OwnerAccountService {
    private static final String ACCOUNT_FIELDS =
            "id,email,role,is_active,created_at,updated_at";

    private final SupabaseDataClient client;

    /** Creates an Owner account service. */
    public OwnerAccountService(SupabaseDataClient client) {
        this.client = Objects.requireNonNull(client);
    }

    /** Lists all Owner accounts visible to the active Owner session. */
    public List<Account> listOwners() {
        JsonNode rows = client.get("accounts?role=eq.OWNER&select=" + ACCOUNT_FIELDS
                + "&order=created_at.asc");
        List<Account> owners = new ArrayList<>();
        rows.forEach(row -> owners.add(account(row)));
        return List.copyOf(owners);
    }

    /** Creates another Owner after verifying the active Owner's current password. */
    public Account createOwner(String email, char[] password, char[] currentPassword) {
        try {
            String normalizedEmail = AccountValidation.normalizeEmail(email);
            AccountValidation.validatePassword(password);
            requireCurrentPassword(currentPassword);
            JsonNode response = client.function("manage-member", Map.of(
                    "action", "create-owner",
                    "email", normalizedEmail,
                    "password", new String(password),
                    "current_password", new String(currentPassword)));
            long accountId = response.path("owner_account_id").asLong();
            if (accountId <= 0) {
                throw new IllegalStateException("GymFlow response is missing the Owner account");
            }
            return loadOwner(accountId);
        } finally {
            clear(password);
            clear(currentPassword);
        }
    }

    /** Activates or deactivates another Owner after recent password verification. */
    public Account setActive(long ownerAccountId, boolean active, char[] currentPassword) {
        try {
            if (ownerAccountId <= 0) {
                throw new IllegalArgumentException("Owner account is required");
            }
            requireCurrentPassword(currentPassword);
            client.function("manage-member", Map.of(
                    "action", "set-owner-active",
                    "owner_account_id", ownerAccountId,
                    "active", active,
                    "current_password", new String(currentPassword)));
            return loadOwner(ownerAccountId);
        } finally {
            clear(currentPassword);
        }
    }

    private Account loadOwner(long accountId) {
        JsonNode rows = client.get("accounts?id=eq." + accountId + "&role=eq.OWNER&select="
                + ACCOUNT_FIELDS + "&limit=1");
        if (!rows.isArray() || rows.size() != 1) {
            throw new IllegalStateException("Owner account was not found");
        }
        return account(rows.get(0));
    }

    private static Account account(JsonNode row) {
        return new Account(row.path("id").asLong(), required(row, "email"),
                Role.valueOf(required(row, "role")), row.path("is_active").asBoolean(),
                Instant.parse(required(row, "created_at")),
                Instant.parse(required(row, "updated_at")));
    }

    private static String required(JsonNode row, String field) {
        String value = row.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalStateException("GymFlow response is missing " + field);
        }
        return value;
    }

    private static void requireCurrentPassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Enter your current password");
        }
    }

    private static void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }
}
