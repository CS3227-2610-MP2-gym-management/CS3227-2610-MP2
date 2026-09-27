# Unified Workout and Visit Refactoring Plan Interaction Summary

## Request

Plan a substantial Member workflow refactor that combines the separate `Gym Visits` and `Workouts` tabs into one
`Workouts` feature while retaining the application's established styles.

The requested target behavior was:

- Treat each gym Visit as exactly one Workout session.
- Create the Workout when the Member checks in and complete it when the Member checks out.
- Derive start and end times from check-in and check-out rather than Member-entered times.
- Require confirmation before both check-in and check-out because these actions create and complete attendance records.
- Allow a Workout to contain no exercises.
- Let a checked-in Member add exercises directly below the check-in area on Member Home.
- Remove duplicate Member Visit/Workout UI, stale implementation code, and superseded tests.
- Provide phased goals, implementation steps, and test gates under the repository's plan directory.

## Repository review and initial findings

- Confirmed that the repository convention is `docs/Plans/`, not a root-level `Plans/` directory.
- Reviewed the current Visit and Workout models, services, stores, database schema, Member Home and navigation,
  Member Visit and Workout views, relevant unit tests, rendered UI tests, and existing Member plan.
- Identified that Visits and Workouts are currently separate persisted aggregates with incompatible rules:
  Workouts are completed, Member-editable/deletable, and require at least one set, while Visits can be open and carry
  Owner correction metadata.
- Identified that Owner Visit oversight must also be migrated because removing only the Member Visit tab would leave
  two authoritative records that could disagree.
- Noted the existing project-local `.gradle` directory, which conflicts with the repository hygiene requirement in
  `AGENTS.md` and must not be staged.

## Initial plan

Created `docs/Plans/unified-workout-visits.md` with:

- target-domain invariants for one authoritative session record;
- likely retained, adapted, and removed classes;
- phased schema, service, Member Home, history/navigation, cleanup, documentation, and verification work;
- transactional, authorization, concurrency, migration, accessibility, rendered-UI, and regression tests;
- explicit phase gates and completion criteria; and
- risks involving ambiguous legacy duplicates, attendance accountability, partial check-out writes, concurrent actions,
  and UI draft loss.

The initial plan recommended preserving Owner correction behavior on the unified Workout, preventing Member time edits,
making check-out atomic, and resolving ambiguous legacy Visit/Workout data before implementation.

## Confirmed decisions and plan refinement

The following decisions were confirmed through follow-up discussion and incorporated into the plan:

- **Unified persistence:** Workout will be the only gym-session entity. The Owner's `Visits` tab will search and correct
  these same Workout records rather than use a separate Visit table.
- **Member permissions:** Members may edit notes and exercises during or after a Workout, but cannot edit its check-in
  or check-out times and cannot delete the Workout.
- **Database transition:** Existing development data may be cleaned up instead of attempting an unreliable merge.
  Implementation must first resolve the exact database path, create and verify a recoverable backup, show the paths,
  and obtain final confirmation before the destructive reset. No database was removed during this planning work.
- **Atomic completion:** Confirmed check-out must save the displayed notes/exercises and close the Workout in one
  transaction. Failure leaves the Workout open and preserves recoverable form state.
- **Time rule:** A completed Workout's end time must be strictly after its start time. An equal or earlier clock value
  is rejected without changing the open Workout.
- **Plan location:** `docs/Plans/` is the correct location.
- **Gradle hygiene:** The existing project-local `.gradle` directory must not be staged and should be removed before
  implementation handoff; normal Gradle caches remain outside the repository.

The revised plan replaces ambiguous legacy Visit/Workout migration with a backed-up clean database transition and
adds strict positive-duration tests, immutable-time and non-deletion tests, and clean-schema/reset coverage.

## Initial testing guidance added

The plan now requires guided post-reset setup through supported application UI flows:

1. Create the initial Owner account.
2. Create a primary Member with an active Membership for successful check-in, exercise, check-out, history, restart,
   and Owner-correction tests.
3. Create Members with expired and upcoming Memberships for rejected check-in tests.
4. Create a Member whose Membership can be deactivated after check-in to verify check-out still succeeds.
5. Record each test account's purpose without storing plaintext passwords; the user selects and retains credentials.
6. Verify Member data isolation and that the Owner sees all unified sessions in `Visits`.

## Files changed

- `docs/Plans/unified-workout-visits.md`
- `logs/isaac/2026-09-27-unified-workout-visits-plan.md`

## Verification

- Ran a consistency search across the revised plan for legacy migration, deduplication, deletion, duration, account
  setup, and `.gradle` references.
- Ran `git diff --check` for the plan.
- No production code, database, account, or Gradle cache was changed.
- No automated application tests were run because the work was limited to planning and conversation documentation.

