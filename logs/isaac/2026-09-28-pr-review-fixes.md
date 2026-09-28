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
