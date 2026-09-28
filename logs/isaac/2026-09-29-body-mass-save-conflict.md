# Body-Mass Save Conflict

**Date:** 29 September 2026  
**Status:** Fixed and verified

## Scope

Investigated an HTTP 409 reported when a Member first saved today's body-mass reading and then tried to add an older
measurement. Traced the JavaFX editor, service, Supabase Data API calls, and database uniqueness constraint; determined
whether local development and production were affected; and implemented and verified a date-keyed save operation.

## Conversation and Findings

- The reported dialog exposed PostgreSQL's duplicate-key error for
  `body_metrics_member_account_id_measurement_date_key`.
- The uniqueness constraint itself was correct: each Member should have at most one body-mass reading per date.
- The editor cached only the reading for `LocalDate.now()` and used that stale state to choose between create and update,
  regardless of the date currently selected by the Member.
- After a completed refresh, choosing an empty past date could incorrectly move today's row to that date instead of
  creating a second row. Before refresh completed, the cached state could still select another insert and expose the
  duplicate-date conflict.
- The Save button remained enabled while the asynchronous request and refresh were running, allowing repeated requests.
- The JavaFX `DatePicker` value was read from a background virtual thread even though JavaFX controls must be accessed on
  the application thread.
- Both development and production were affected because the application uses the Supabase-backed service in both modes;
  only the configured Supabase URL changes. Both databases use the same unique member/date constraint. The legacy SQLite
  implementation had the same date uniqueness rule, although its error would not be presented as HTTP 409.

## Changes

- Added a service-level save operation that creates or updates the authenticated Member's reading for the selected date.
- Added an atomic Supabase/PostgREST upsert using the conflict target
  `(member_account_id, measurement_date)` and `resolution=merge-duplicates`.
- Added equivalent SQLite `ON CONFLICT ... DO UPDATE` behavior so both persistence implementations share the same
  semantics.
- Changed the body-mass editor to capture the selected date and weight on the JavaFX application thread before starting
  background work.
- Removed the stale today-only create/update decision from the editor.
- Disabled Save while a request is in flight and restored it after success or failure.
- Added regression coverage that saves today, adds a past reading, corrects the past reading, and verifies that both dates
  remain with only the selected row updated.

## Verification

- Ran the focused `BodyMetricServiceTest` and `MemberBodyMetricsViewTest` tests successfully.
- Ran `gradlew.bat checkstyleMain` successfully.
- Ran the complete `gradlew.bat check` verification suite successfully, including all Java tests and Checkstyle tasks.
- Used the user-level Gradle cache outside the repository. No new project-local Gradle cache was created.

## Suggested Commit Message

`fix(member): upsert body-mass readings by selected date`
