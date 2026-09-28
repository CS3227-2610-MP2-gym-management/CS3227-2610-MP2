# Production Migration and Membership Fixes

## Scope

Investigated a multi-instance production test in which Owner and Member applications displayed generic connection or
data-access errors. The conversation covered the distinction between local and hosted Supabase, terminal-specific
production environment variables, migration deployment in plain language, release ordering, and hands-on production
verification. It then addressed failures in Membership deactivation, Owner Member-profile editing, and Member tracking
despite the UI showing a current Membership.

## Findings

- The desktop application no longer uses the legacy SQLite database at runtime. Multiple instances are supported by
  hosted Supabase/PostgreSQL, so the failures were not a single-access database lock.
- A PowerShell `$env:` value applies only to the terminal and its child processes. Each independently launched
  production application therefore needs the production URL and publishable key in its own environment.
- Production initially contained migrations only through `20260928092000`, while the application called
  `owner_set_membership_active`, introduced in `20260928094000`. The UI hid the resulting backend failure behind
  `Unable to access GymFlow data`.
- The database migration suite contained an older schema fixture that attempted to create tracking records without
  the current Membership required by the newest trigger.
- Membership status used the desktop's local calendar date, while the PostgreSQL tracking trigger used the database
  session's `current_date`. Around midnight in Singapore, the UI could classify a Membership as current while the
  hosted database, still on the previous UTC date, rejected new activity with HTTP 403.
- Owner profile editing always attempted a Supabase Auth email update, even when the normalized email was unchanged.
  This unnecessary operation could prevent otherwise unrelated profile changes.
- The final hosted profile-update failure was a permissions defect: `update_member_records` was `SECURITY INVOKER`,
  but hosted `service_role` had only `SELECT` privileges on `accounts` and `member_profiles`. The Edge Function masked
  the PostgreSQL permission error as HTTP 400 `Unable to update Member account`. The local privileged context had not
  exposed this production-specific mismatch.

## Changes

- Expanded `README.md` and `docs/ProductionDeployment.md` with a layperson explanation of migrations, when to run
  local reset versus hosted push commands, command purposes, safeguards, and the required release order:
  migrations, Edge Functions, smoke tests, then release JARs.
- Updated the older schema fixture with current Memberships so it continues testing its intended Workout and body
  metric constraints under the new tracking rule.
- Added `20260929001000_use_singapore_membership_date.sql` so database tracking enforcement consistently uses the
  `Asia/Singapore` business date.
- Changed `manage-member` to skip Supabase Auth email mutation and rollback when the normalized Member email is
  unchanged. Added focused tests for that decision.
- Allowed the Owner edit dialog to display safe `IllegalStateException` operation messages instead of replacing every
  backend failure with a generic data-access message.
- Added `20260929002000_protect_member_profile_update.sql`, making the narrowly scoped, service-role-only
  `update_member_records` function `SECURITY DEFINER`. Direct execution remains unavailable to ordinary authenticated
  desktop users.
- Added regression coverage for the Singapore date rule, profile-update security mode, `service_role` execute access,
  and denial of direct authenticated execution.
- Applied all pending migrations to the linked GymFlow Production project through the guarded dry-run, backup, and
  push script. Deployed the current `manage-member` Edge Function and confirmed local/remote migration parity through
  `20260929002000`.

## Verification

- Rebuilt disposable local Supabase from the full committed migration history, including both new migrations.
- `npm.cmd run supabase:test`: 176 database assertions passed.
- `npm.cmd run supabase:lint`: no schema warnings or errors.
- `npm.cmd run test:edge-functions`: 3 tests passed.
- `gradlew.bat checkstyleMain checkstyleTest test verifyLocal`: Java tests, all Checkstyle tasks, rendered UI tests,
  release-JAR construction, and release-JAR validation passed.
- A disposable integration request signed in as the seeded Owner, saved a seeded Member's name, phone, and birth date
  without changing the email, and read the updated fields back successfully.
- Guarded production pushes created timestamped ignored backups under `work/production-backups` before changing the
  hosted database. `supabase migration list` reported all 22 migrations present locally and remotely.
- `git diff --check` reported no whitespace errors; Git emitted only the repository's existing LF-to-CRLF warnings.

## Suggested commit message

`fix: align production membership and profile updates`
