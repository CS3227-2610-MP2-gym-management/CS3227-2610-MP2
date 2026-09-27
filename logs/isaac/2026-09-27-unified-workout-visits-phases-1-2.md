# Unified Workout/Visits Phases 1 and 2

## Scope

Implemented the first two phases of `docs/Plans/unified-workout-visits.md`: establish the clean unified Workout
persistence model, then route check-in/check-out and Owner attendance oversight through that model.

## Phase 1: unified persistence

- Advanced the SQLite schema to version 9.
- Removed `visits` and its Visit-only index from the latest clean schema.
- Made `workouts.ended_at` nullable for an open Workout and added correction metadata inherited from Owner attendance
  oversight.
- Added the `one_open_workout_per_member` partial unique index and retained the Workout-to-set cascade and ordered-set
  constraints.
- Removed legacy Visit/Workout imports. Existing development data is intentionally not merged; the verified reset flow
  creates the clean schema instead.
- Allowed empty Workout set collections and nullable completion times in the model, store, and validation path.
- Updated `docs/DeveloperGuide.md` to record schema version 9 and the clean-reset/no-import transition.
- Added coverage for clean initialization, absence of the `visits` table, reset behavior, the open-Workout constraint,
  strict completed ranges, and persistence of an empty open Workout across restart.

## Phase 2: service and ownership consolidation

- Repointed Member check-in, check-out, state, and history persistence from `visits` to `workouts`.
- Check-in remains transactional and validates an active Member plus a Membership valid on the local clock date.
- Check-out does not recheck Membership validity, requires a strictly later timestamp, and uses a guarded update so a
  stale or repeated action cannot close a session twice.
- Repointed Owner search, history, current-visitor counting, and correction persistence to unified Workout records.
- Owner corrections retain their authorization, reason, correction metadata, range validation, and one-open-session
  protection.
- Ensured Member exercise replacement preserves a Workout's recorded start/end timestamps.
- Kept the existing Visit-named UI-facing types temporarily because their caller migration is scheduled for the later
  Member Home and navigation phases; their data access now targets the authoritative Workout records.

## Verification

The Gradle wrapper initially could not create its normal cache lock in this execution environment. Verification then
used `C:\tmp\gymflow-gradle`, outside the repository, so no project-local Gradle cache was created.

The following focused verification passed:

```bash
./gradlew test \
  --tests com.gymflow.data.GymFlowDatabaseTest \
  --tests com.gymflow.workout.WorkoutServiceTest \
  --tests com.gymflow.visit.MemberVisitServiceTest \
  --tests com.gymflow.visit.OwnerVisitServiceTest
./gradlew checkstyleMain
git diff --cached --check
```

The SQLite/Java runtime printed its restricted-native-access warning only; it was not a test or Checkstyle failure.

## Handoff

- The Phase 1 commit is `179eff5` (`Refactor to introduce unified workout session schema`).
- The Phase 2 implementation and tests are staged but intentionally not committed.
- Suggested Phase 2 commit message: `refactor: route visit services through unified workouts`.
- Phase 3 remains responsible for moving the active Workout workspace, exercise draft, and confirmation UI onto
  Member Home. Phase 4 then removes the Member Visit route and remaining compatibility UI.
