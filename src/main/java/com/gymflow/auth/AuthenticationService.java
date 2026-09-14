package com.gymflow.auth;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import com.gymflow.data.AccountStore;
import com.gymflow.data.GymFlowDatabase;
import com.gymflow.data.StoredAccount;
import com.gymflow.model.Account;
import com.gymflow.model.Role;

/** Handles account setup and credential verification. */
public final class AuthenticationService {
    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final int MAXIMUM_PASSWORD_LENGTH = 128;
    private final AccountStore accounts;
    private final PasswordHasher passwords;
    private final GymFlowDatabase database;

    /** Creates an authentication service backed by the supplied database. */
    public AuthenticationService(GymFlowDatabase database) {
        this.database = database;
        accounts = new AccountStore(database);
        passwords = new PasswordHasher();
    }

    /** Returns whether this installation already has an Owner. */
    public boolean hasOwner() {
        return accounts.findOwner().isPresent();
    }

    /** Creates the installation's only Owner account. */
    public Account createOwner(String email, char[] password) {
        try {
            String normalizedEmail = validateEmail(email);
            validatePassword(password);
            if (hasOwner()) {
                throw new IllegalArgumentException("Owner account already exists");
            }
            return accounts.create(normalizedEmail, passwords.hash(password), Role.OWNER);
        } finally {
            clear(password);
        }
    }

    /** Returns the active account matching the supplied credentials. */
    public Optional<Account> authenticate(String email, char[] password) {
        try {
            if (email == null || password == null) {
                return Optional.empty();
            }
            Optional<StoredAccount> stored = accounts.findByEmail(email);
            if (stored.isEmpty() || !stored.get().account().active()
                    || !passwords.verify(password, stored.get().password())) {
                return Optional.empty();
            }
            return Optional.of(stored.get().account());
        } finally {
            clear(password);
        }
    }

    /** Permanently clears all application data after verifying both reset guards. */
    public void resetAll(Account owner, char[] currentPassword, String confirmation) {
        try {
            Optional<StoredAccount> storedOwner = accounts.findOwner();
            boolean authorized = owner != null
                    && storedOwner.isPresent()
                    && storedOwner.get().account().id() == owner.id()
                    && "RESET".equals(confirmation)
                    && currentPassword != null
                    && passwords.verify(currentPassword, storedOwner.get().password());
            if (!authorized) {
                throw new IllegalArgumentException("Reset authorization failed");
            }
            database.reset();
        } finally {
            clear(currentPassword);
        }
    }

    private static String validateEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (at <= 0 || at != normalized.lastIndexOf('@') || at == normalized.length() - 1) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        return normalized;
    }

    private static void validatePassword(char[] password) {
        int length = password == null ? 0 : password.length;
        if (length < MINIMUM_PASSWORD_LENGTH || length > MAXIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be between 12 and 128 characters");
        }
    }

    private static void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
