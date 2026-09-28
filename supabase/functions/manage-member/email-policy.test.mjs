import assert from "node:assert/strict";
import test from "node:test";

import {
  DUPLICATE_EMAIL_CODE,
  DUPLICATE_EMAIL_MESSAGE,
  isDuplicateEmailError,
  normalizeAccountEmail,
  shouldUpdateLoginEmail,
} from "./email-policy.mjs";

test("normalizes account emails before cross-role comparison", () => {
  assert.equal(normalizeAccountEmail("  Owner@Example.COM  "), "owner@example.com");
});

test("updates the Auth email only when the normalized address changes", () => {
  assert.equal(shouldUpdateLoginEmail("Member@Example.com", " member@example.COM "), false);
  assert.equal(shouldUpdateLoginEmail("old@example.com", "new@example.com"), true);
});

test("recognizes Supabase Auth and PostgreSQL duplicate-email failures", () => {
  assert.equal(isDuplicateEmailError({ code: "email_exists" }), true);
  assert.equal(isDuplicateEmailError({
    code: "23505",
    message: 'duplicate key value violates unique constraint "accounts_email_unique"',
  }), true);
  assert.equal(isDuplicateEmailError({ message: "User already registered" }), true);
  assert.equal(isDuplicateEmailError({
    message: "A user with this email address has already been registered",
  }), true);
  assert.equal(isDuplicateEmailError({
    code: "23505",
    message: 'duplicate key value violates unique constraint "member_number_unique"',
  }), false);
  assert.equal(isDuplicateEmailError({ message: "Connection refused" }), false);
  assert.equal(DUPLICATE_EMAIL_CODE, "duplicate_email");
  assert.equal(DUPLICATE_EMAIL_MESSAGE, "An account with this email already exists");
});
