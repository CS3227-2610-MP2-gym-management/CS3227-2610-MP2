package com.gymflow.data;

import com.gymflow.auth.PasswordHash;
import com.gymflow.model.Account;

/** Account details paired with password material for authentication. */
public record StoredAccount(Account account, PasswordHash password) {
}
