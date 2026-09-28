# PR Review Fixes

**Date:** 28 September 2026  
**Status:** Completed; Supabase execution pending Docker or CI
**Branch:** `feature/itzxitzx-deploy-for-production`

## Fix 1: Server-owned Member Visit timestamps

Member check-in and check-out no longer accept a timestamp from the desktop client. The protected PostgreSQL
functions capture `now()` and use that value for both validation and storage. Check-in derives the applicable
Membership date in the gym's `Asia/Singapore` timezone. Check-out now also confirms that the caller still has an
active Member account.

The Supabase Visit store calls the new parameterless operations. Database tests verify that only the parameterless
signatures exist, preventing a modified client from invoking the former backdating API.

### Verification

- Reset the local Supabase database so migration `20260928093000` is applied.
- Run the Supabase database tests and confirm `phase_5_visits.test.sql` passes.
- Run `gradlew.bat checkstyleMain test` for Java style and regression coverage.
- Manually check in and out as a seeded Member and confirm the displayed Visit times match the server's current time.

Automated Java verification passed with an external temporary Gradle cache. The local Supabase reset and pgTAP run
could not execute because Docker Desktop's Linux engine was not running; those commands remain the required database
verification once Docker is available.

## Fix 2: Protected Membership activation

Membership activation and deactivation now use the `owner_set_membership_active` security-definer function instead
of a direct REST update. The function requires an active Owner, locks and validates the target Membership, rejects
expired reactivation using Singapore's current date, and rejects overlap with another active Membership. The existing
database exclusion constraint remains the final concurrency safeguard.

Authenticated desktop users no longer have direct update permission on Membership state. Database tests cover Owner
deactivation, valid reactivation, expired reactivation, overlapping reactivation, function access, and removal of
direct table-update access.

### Verification

- Reset local Supabase and run `npm run supabase:test`.
- Run `gradlew.bat checkstyleMain test`.
- As an Owner, deactivate and reactivate a current Membership; then confirm an expired Membership cannot be
  reactivated and an overlapping Membership is rejected.

Java Checkstyle and regression tests passed. The pgTAP cases are ready but require the currently stopped Docker
Desktop Linux engine before they can be executed locally.

## Fix 3: Reject unchanged Visit corrections

The Owner Visit correction function now locks and loads the existing Visit before writing correction metadata. It
uses null-safe timestamp comparisons and rejects a correction unless entry time, exit time, or both actually change.
Not-found Visits and unchanged Visits now produce distinct database errors.

The Visit pgTAP suite repeats a correction with the stored timestamps and verifies that it is rejected.

### Verification

- Reset local Supabase and run `npm run supabase:test`.
- Run `gradlew.bat checkstyleMain test`.
- In Owner Visits, submit the existing entry and exit values with a new reason and confirm it is rejected; then
  change either timestamp and confirm the correction and audit metadata are saved.

Java Checkstyle and regression tests passed. Database execution remains pending until Docker Desktop is running.

## Fix 4: Refresh authentication after Member email changes

The Supabase authentication session now retains the Auth user identifier and can reload its application Account.
After a Member changes their own email, `MemberAccountService` refreshes that session Account. Cloud password changes
reload the current identity and authenticate with its current email instead of the stale Account object captured by
the JavaFX screen.

The environment-gated Supabase integration flow now changes a Member's email and immediately changes the password
using the original Account object, then confirms sign-in succeeds with the new email and password.

### Verification

- Run `gradlew.bat checkstyleMain test`.
- With local Supabase running, set `GYMFLOW_LOCAL_INTEGRATION=true` and run the Java integration tests.
- Manually sign in as a Member, change the email, then change the password without signing out. Sign out afterward
  and confirm the new email and password authenticate successfully while the old email does not.

Java Checkstyle and regression tests passed. The end-to-end stale-Account scenario is included in the gated cloud
integration test and requires local Supabase to execute.

## Fix 5: Search by Member name or email only

Supabase searches for Members, Memberships, Payments, and Visits no longer match Member numbers. Member numbers
remain present in result models and UI labels as stable identifiers after an Owner finds a record. This aligns cloud
behavior with the existing SQLite contract and all four search prompts.

The Supabase integration test now asserts that a known seeded Member number returns no results from each search.

### Verification

- Run `gradlew.bat checkstyleMain test`.
- With local Supabase running, enable and run the cloud integration tests.
- On each Owner Members, Memberships, Payments, and Visits screen, verify a name fragment and email fragment match,
  while `M0001` returns no results.

Java Checkstyle and regression tests passed. The four cloud assertions require local Supabase for execution.

## Fix 6: Prevent repeated announcement withdrawal

Announcement withdrawal now uses an active-Owner security-definer function. The update succeeds only while
`withdrawn_at` is null, so a missing or already withdrawn announcement is rejected without rewriting its audit time.
Authenticated clients no longer have direct permission to update announcement withdrawal fields.

A dedicated pgTAP test covers permissions, successful withdrawal, repeated withdrawal, and Member denial. The Java
cloud integration test also attempts a second withdrawal and expects it to fail.

### Verification

- Reset local Supabase and run `npm run supabase:test`.
- Run `gradlew.bat checkstyleMain test` and the environment-gated cloud integration test.
- Publish and withdraw an announcement as an Owner. A second withdrawal through the service should fail, and the
  original `withdrawn_at` value should remain unchanged.

Java Checkstyle and regression tests passed. The database and cloud withdrawal cases require local Supabase.

## Fix 7: Deterministic cloud ordering

Supabase Member, Payment, Membership, Visit, Announcement, and Expense queries now include stable ID tie-breakers.
Member lists use Member number ascending after name; date-ordered records use ID descending after their primary date.
Withdrawn announcements now sort by withdrawal time and ID, matching the SQLite behavior, rather than by their old
publication time. Expense ordering now uses expense date and ID consistently with SQLite.

### Verification

- Run `gradlew.bat checkstyleMain test` and the cloud integration test.
- Create at least two records of each type with the same primary date or timestamp, refresh repeatedly, and confirm
  the higher ID always appears first. For withdrawn announcements, confirm the most recently withdrawn appears first
  even when it was originally published earlier.

Java Checkstyle and regression tests passed.

## Fix 8: Exercise Supabase behavior in CI

The Tests workflow now has a separate Ubuntu Supabase integration job. It installs the pinned repository-compatible
CLI, starts the local Docker stack, resets it through every migration and the seed, runs all pgTAP database tests,
and then runs the Java tests with cloud integration enabled. An `always()` cleanup step stops the stack even after a
failure. The existing four-platform Java and release-JAR matrix remains unchanged.

### Verification

- Push the branch and confirm the `supabase-integration` job appears alongside all four matrix jobs.
- Inspect its log to confirm every migration through `20260928096000` applies, every `supabase/tests` file passes,
  and `SupabaseAuthenticationIntegrationTest` plus `SupabaseFeatureIntegrationTest` execute rather than skip.
- Confirm cleanup runs on both success and a deliberately failing test branch.

The full local Gradle `check` task passed, including Checkstyle for main, test, and rendered UI sources. The Supabase
job itself could not be reproduced in this session because Docker Desktop's Linux engine was not running; the new CI
job is the independent verification gate for all database and cloud behavior.

## Commit sequence

| Fix | Commit | Outcome |
| --- | --- | --- |
| 1 | `8f8e008` | Server-owned Member Visit timestamps |
| 2 | `2450c93` | Protected Membership reactivation |
| 3 | `a548a2b` | Rejection of unchanged Visit corrections |
| 4 | `fc1e489` | Authentication refresh after Member email updates |
| 5 | `b8043e1` | Name-or-email-only cloud search |
| 6 | `b394e6d` | Protected one-time announcement withdrawal |
| 7 | `c28926a` | Deterministic cloud query ordering |
| 8 | Final commit in this sequence | Supabase database and Java integration coverage in CI |

## Follow-up: Visit test transaction timestamps

The first local pgTAP run exposed a PostgreSQL time-semantics issue: `now()` is fixed at transaction start, while the
Visit test intentionally performs check-in and checkout inside one transaction. Both operations therefore received
the same timestamp and checkout correctly refused to create a zero-duration Visit.

Migration `20260928097000` changes the two server-owned Visit timestamps to `statement_timestamp()`. The value is
still assigned entirely by PostgreSQL, but reflects the start of each check-in or checkout statement rather than the
start of the surrounding transaction. This preserves the security fix and makes consecutive operations correct in
both transactional tests and normal RPC use.

A clean local reset applied every migration through `20260928097000`. The complete pgTAP suite then passed all 157
tests, including all 17 Visit tests and the two check-out assertions that originally failed. The full Gradle `check`
task also passed, including Checkstyle for every Java source set.

## Follow-up: Blank Member window after Membership activation

Smoke testing exposed a JavaFX `NullPointerException` after a Membership state change refreshed the Member details
screen. Supabase represented a Payment without a reference as Java `null`, while the equivalent SQLite mapper and
the Payment UI contract use an empty string. Rebuilding Payment history called `isBlank()` on that null reference,
interrupting screen construction after the Membership update had already succeeded.

The Supabase Payment mapper now normalizes a database null reference to an empty string. A focused mapping test
covers this case so Member details can safely refresh after Membership activation or deactivation.

The full Gradle `check` task passed. After resetting smoke-test data to the seeded baseline, all 157 Supabase tests
also passed. Running pgTAP before the reset correctly exposed overlapping Membership data left by manual testing;
the suite is designed to run after `npm run supabase:reset`.
