# Cross-role email exclusivity

## Goal

Ensure that one normalized email identifies one GymFlow account with one permanent Owner or Member role.

## Important prompts and decisions

- Dylan requested logic preventing an Owner email from being reused for a Member and vice versa.
- Existing Supabase Auth uniqueness and the global case-insensitive `accounts_email_unique` index were retained as
  final safeguards rather than replaced with role-specific indexes.
- The protected account function now performs an earlier cross-role check and returns one safe duplicate-email
  message. The Java client exposes only this structured validation error to forms while keeping unknown backend
  failures generic. Account roles are immutable; supported co-owners receive separate accounts.
- The documented project assumptions are that each person uses one email and authenticated Owners do not deliberately
  abuse their administrative access.

## Affected components

- Protected Owner/Member account operations and email normalization
- PostgreSQL account-role integrity
- Supabase and integration regression tests
- User, developer, and project-decision documentation

## Verification

- Pending Dylan review.
- `npm run test:edge-functions` passed both email-policy tests.
- `deno check` passed for the protected `manage-member` function.
- `SupabaseDataClientTest` verifies that duplicate-email validation is displayed while unknown HTTP errors remain
  non-sensitive.
- `./gradlew clean check checkstyleMain verifyReleaseJars --no-watch-fs` passed.
- Docker-backed Supabase database and integration tests could not be run inside the Codex sandbox because it could
  not access the local Docker socket; those tests remain part of the manual handoff.
