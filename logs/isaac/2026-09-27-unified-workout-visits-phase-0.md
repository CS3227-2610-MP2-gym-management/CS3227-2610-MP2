# Unified Workout/Visits Phase 0 Baseline

## Scope

This record establishes the pre-refactor baseline for `docs/Plans/unified-workout-visits.md`. The target design uses
`Workout` as the only persisted gym-session record: a Member's check-in/check-out times are immutable, Workouts may
have no sets, and Owner oversight reads and corrects those same records.

## Product-contract preparation

- Updated the affected Member and Owner stories in `docs/UserStories.md` to describe unified Workouts rather than
  independently mutable Visits and Workouts.
- Made the key target constraints explicit: check-in/check-out set the immutable session times, an empty Workout is
  valid, Members may edit only notes/exercises, and Members cannot delete a Workout.

## Reference inventory

The following pre-refactor references were found and must be migrated or intentionally retained in later phases:

- Production Visit boundary: `Visit`, `VisitOverview`, `MemberVisitState`, `MemberVisitStore`, `VisitHistoryStore`,
  `OwnerVisitStore`, `MemberVisitService`, `OwnerVisitService`, `MemberVisitsView`, `OwnerVisitsView`, and
  `VisitFormat`.
- Navigation/routing: `MEMBER_VISITS` in `Screen` and `AppView`, the `Gym Visits` Member navigation entry, and
  Member-Visit authorization tests.
- Workout boundary: `Workout`, `WorkoutSet`, `WorkoutSetInput`, `SaveWorkoutRequest`, `WorkoutStore`,
  `WorkoutService`, and `MemberWorkoutsView` include the current completed-only/manual-recording and deletion flow.
- Persistence: `GymFlowDatabase` currently creates separate `visits`, `workouts`, and `workout_sets` tables; the
  Visit-only partial index enforces one open Visit per Member.
- Tests: focused Visit service tests, Workout service tests, database migration/reset tests, Member Home/Workouts
  UI tests, rendered Member shell tests, and route authorization tests require replacement or adaptation.
- Documentation: `README.md`, `ARCHITECTURE.md`, `docs/UserGuide.md`, `docs/DeveloperGuide.md`,
  `docs/ProjectDecisions.md`, `docs/UserStories.md`, contribution/reflection documents, and prior Isaac logs contain
  Visit/Workout, manual-recording, Member-deletion, or one-set-minimum language.

## Baseline verification

Run on 2026-09-27 with the normal user-level Gradle cache (`C:\\Users\\isaac\\.gradle`), outside the repository:

```bash
./gradlew.bat test renderedUiTest checkstyleMain
```

Result: **BUILD SUCCESSFUL**. Unit tests, rendered-UI tests, and `checkstyleMain` passed. The only console output was
the Java 25 restricted-native-access warning from SQLite/JavaFX; it was not a test or Checkstyle failure.

## Database reset preparation

- Resolved live development database: `data/gymflow.db`.
- This is the application path set by `GymFlowApp`; temporary test databases are not reset targets.
- Created and verified backup:
  `work/backups/gymflow-before-unified-workout-visits-20260927-223314.db`.
- Source and backup are each 90,112 bytes and have matching SHA-256:
  `56FB019F7ACC24AEBB6964C99C53BF4DB89FF4903379A74C4355AD4A9FCD4E89`.
- The backup is outside the precise reset target and is ignored by Git.

## Pending destructive action

Do not reset yet. Immediately before reset, show these verified paths again and obtain explicit confirmation to reset
only `data/gymflow.db` through the application reset mechanism or by removing that exact file. The backup must remain
untouched.
