# CI Supabase Session Fixes

## Scope

Diagnosed two CI failures: the Supabase feature integration test failing during a Member self-service contact update,
and the Windows build failing before tests while Maven Central returned HTTP 403 for a JavaFX plugin dependency.
Implemented the concise application and workflow fixes and supplied a commit-message recommendation.

## Diagnosis

- The Supabase stack was already running: startup, database reset, SQL tests, authentication, account creation, and
  earlier Edge Function operations completed before the integration test reached the failing line.
- The failing call changed the currently authenticated Member's email and then tried to refresh the existing Auth
  session. An email change is security-sensitive, so the old refresh-token session could no longer be assumed valid.
- `refreshAccount()` mixed two responsibilities by forcing an Auth token refresh before reloading the application
  account. The CI output suppressed the underlying HTTP response and only reported the integration-test line.
- The Windows job did not run tests. It failed during Gradle configuration because Maven Central returned HTTP 403
  while resolving `com.google.gradle:osdetector-gradle-plugin:1.7.3`, a transitive JavaFX plugin dependency.
- Java unchecked-operation and SQLite native-access messages were warnings and were unrelated to either failure.

## Implementation

- Changed Member contact updates to require the current password and clear its character array after use.
- Verified the password before changing local or Supabase-backed contact information.
- After a cloud email update, authenticated with the new email and current password to establish a fresh Supabase
  session instead of refreshing a potentially stale session.
- Added a current-password field to the Member contact-details form.
- Updated unit and Supabase integration tests for the new contact-update contract.
- Added `gradle/actions/setup-gradle@v4` to both CI jobs to restore and populate Gradle dependency caches.
- Enabled `--stacktrace --info` for the Supabase integration run so future failures expose actionable details.

## Validation

- `gradlew.bat --no-daemon test checkstyleMain` passed.
- `gradlew.bat --no-daemon check` passed, including main, test, and rendered-UI Checkstyle tasks.
- The Supabase integration test remains environment-gated and must be exercised by the CI Supabase stack.
- No project-local Gradle cache was created.

## Suggested Commit Message

`Fix member session recovery and stabilize Gradle CI`
