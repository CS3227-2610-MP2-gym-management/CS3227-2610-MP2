# Member Home Workout State Regression

## Scope

Investigated prior Workout UI decisions recorded under `logs/isaac`, diagnosed the reported Member Home state reset
after navigation, and restored the intended active-Workout and exercise-draft persistence for the Supabase backend.

## Log review

- Confirmed that the `Record Workout` button had not previously been removed. The earlier Workout log only recorded
  removal of the generated bottom-right Cancel button while retaining the inline Cancel action.
- Confirmed that the separate `Save exercises` button was removed during unified Workout/Visit Phase 3 because
  navigation away from Home was intended to save the active draft automatically.
- The Phase 3 log explicitly recorded navigation autosave and incomplete-exercise persistence as implemented and
  manually covered behavior. The reported Home -> Announcements -> Home reset was therefore a regression.

## Reported regression

After a Member checked in, navigated to Announcements, and returned to Home, the Home UI appeared checked out and
discarded the visible exercise workspace. A second Check in attempt returned HTTP 409 because the backend still had
an open Visit. Home was expected to remain visibly checked in and restore any saved notes and exercises.

## Root cause

The earlier local SQLite implementation used Workout as the unified gym-session record. The later Supabase deployment
reintroduced separate `visits` and `workouts` persistence. Cloud check-in created only an open Visit, while the rebuilt
Home view restored its active workspace by searching Workout history for an open Workout. This allowed the backend
and frontend to disagree: the Visit blocked another check-in, but no open Workout existed for Home to display.

Cloud check-out also reused the general Workout save operation with a null end time, so it did not provide the intended
server-timestamped, atomic close of the active Workout and compatibility Visit.

## Implementation

- Added migration `20260928098000_restore_unified_workout_sessions.sql`.
- Check-in now creates the compatibility Visit and authoritative open Workout in one database transaction using the
  same server-owned timestamp.
- The migration backfills an open Workout for an existing open Visit when no open Workout exists, recovering Members
  already stuck in the 409 state.
- Navigation draft saves continue updating the open Workout, including notes and ordered exercise sets. Recreating
  Home therefore reloads the same open Workout and restores its editor contents.
- Added the protected `check_out_member_workout` operation to save the displayed draft and close both the Workout and
  compatibility Visit atomically with a server-owned timestamp.
- Updated `SupabaseWorkoutStore` to call the dedicated check-out operation and reload the saved Workout through one
  shared lookup path.
- Extended the opt-in Supabase Java integration flow to cover check-in, open-Workout discovery, exercise draft save,
  state reload, and Workout-driven check-out.
- Added `phase_10_unified_workout_state.test.sql` so the database contract permanently covers the navigation scenario.

## Verification

```bash
./gradlew.bat test checkstyleMain
./node_modules/.bin/supabase.cmd migration up --local
./node_modules/.bin/supabase.cmd test db supabase/tests/phase_10_unified_workout_state.test.sql
git diff --check
```

Java tests and Checkstyle passed. The migration applied successfully to the running local Supabase database, and all
eight focused pgTAP regression assertions passed. `git diff --check` passed. The full database suite was also attempted
without resetting the local database, but unrelated pre-existing local Membership and open-Workout rows conflicted
with several older tests that assume clean seed data; the focused regression file itself passed.
