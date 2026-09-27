# Unified Workout/Visits Phase 3

## Scope

Implemented the Member Home active-Workout workspace from `docs/Plans/unified-workout-visits.md`, including the
follow-up interaction fixes identified during manual testing.

## Implementation

- Check-in and check-out require confirmation. Check-out atomically saves the current exercise/notes draft.
- Home displays the active Workout, exact local start clock time, notes, and the shared exercise/set editor.
- Navigation away from Home automatically persists the active draft; the separate Save exercises button was removed.
- Empty Workouts and named exercises with incomplete set measures persist and can be completed later.
- Check-out requires a minimum elapsed duration of one minute and keeps the Member checked in after a failed attempt.
- Completed Workout history remains available while checked in; the open Workout is excluded from that history view.
- Existing Workout date and time controls are read-only and render the exact persisted timestamps.
- Refined editor layout so Add Set shares the exercise heading row and the set count remains visible.

## Verification

```bash
./gradlew.bat test checkstyleMain
git diff --cached --check
```

Manual cases covered: confirm/cancel check-in and check-out; immediate check-out rejection; empty check-out;
incomplete-exercise persistence; navigation autosave; completed-history access while checked in; exact immutable edit
times; and light/dark editor readability.
