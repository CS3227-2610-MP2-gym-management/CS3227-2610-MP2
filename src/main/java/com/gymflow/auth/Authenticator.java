package com.gymflow.auth;

import java.util.Optional;

import com.gymflow.model.Account;

/** Authenticates users and owns the active login session. */
public interface Authenticator {
    /** Returns the active account when the supplied credentials are valid. */
    Optional<Account> authenticate(String email, char[] password);

    /** Clears the active login session. */
    default void signOut() {
    }
}
