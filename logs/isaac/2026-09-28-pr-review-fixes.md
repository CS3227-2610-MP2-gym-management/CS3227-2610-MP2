# PR Review Fixes

**Date:** 28 September 2026  
**Status:** In progress  
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
