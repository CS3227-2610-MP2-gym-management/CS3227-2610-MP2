# Unified Gym Visits and Workouts Refactoring Plan

## Outcome

Replace the Member-facing `Gym Visits` and `Workouts` concepts with one `Workouts` feature in which:

- confirming **Check in** creates one open Workout whose start time is the confirmed check-in time;
- confirming **Check out** completes that same Workout using the confirmed check-out time;
- a Workout may contain no exercises;
- a checked-in Member can add and edit exercises directly below the check-in status on Member Home;
- historical Workouts remain available from the single `Workouts` navigation item; and
- the existing visual language, asynchronous loading pattern, authorization boundaries, accessibility, and light/dark themes are preserved.

This is a domain consolidation, not only a navigation change. The target should have one authoritative session record and no independently mutable Visit/Workout pair that can drift out of sync.

## Confirmed product decisions

These decisions are settled requirements for implementation:

1. **One authoritative entity.** Workout is the only persisted gym-session entity. The Owner's `Visits` tab reads and corrects these same Workout records; it does not retain a separate Visit table or record.
2. **Member permissions.** Members may edit notes and exercises during or after a Workout, but may never edit its check-in/check-out times or delete the Workout. Owner correction remains the controlled way to fix attendance times.
3. **Clean database transition.** Existing development data may be discarded to avoid an ambiguous Visit/Workout merge. Before the destructive reset, confirm the exact database path, make a recoverable backup, and obtain final confirmation at implementation time. Create the new unified schema cleanly rather than guessing which old rows are duplicates.
4. **Atomic check-out.** The check-out confirmation saves the current exercise/notes draft and closes the Workout in one transaction. A validation or persistence failure leaves the Workout open and keeps the form contents visible.
5. **Strict positive duration.** `ended_at` must be strictly after `started_at`. If a check-out clock value is equal to or earlier than the recorded start, reject it without changing the Workout and ask the Member to retry.
6. **Plan location.** This plan and subsequent feature plans belong in `docs/Plans/`.
7. **Repository hygiene.** The existing project-local `.gradle` directory must not be staged and should be removed before implementation handoff, in accordance with `AGENTS.md`. Gradle execution should use its normal cache outside the repository.

## Invariants for the target design

- There is at most one open Workout per Member.
- An open Workout has `started_at` and no `ended_at`; a completed Workout has both, with `ended_at` strictly after `started_at`.
- `started_at` is set only by confirmed check-in, and `ended_at` only by confirmed check-out or an authorized Owner correction.
- Exercise sets are an ordered zero-to-many child collection.
- Check-in still requires an active Member and a Membership valid on the current local date.
- Check-out does not recheck Membership validity.
- A cancelled confirmation performs no database write and does not clear draft UI state.
- The service/store layer, not disabled buttons or dialogs, enforces every invariant.
- Check-in, check-out, and exercise replacement are transactional and actor-owned.
- Owner and Member views read the same session record.
- UI work continues to follow `UI -> service -> store -> SQLite`, with database work off the JavaFX Application Thread.
- Time-dependent behavior uses injected `Clock`; timestamps are stored as UTC instants and displayed in the local zone.

## Likely code impact

### Retain and adapt

- `Workout`, `WorkoutSet`, `WorkoutSetInput`, `WorkoutService`, and `WorkoutStore`
- `MemberVisitState` (prefer renaming to `MemberWorkoutState` if the migration cost is acceptable)
- `MemberHomeView`, `MemberWorkoutsView`, `OwnerVisitsView`, and `VisitFormat`
- the existing shared shell, cards, buttons, dialogs, spacing, typography, and CSS classes

### Remove after callers are migrated

- `MemberVisitsView`
- `MEMBER_VISITS` and its navigation mappings
- the standalone Member Visit history path in `MemberVisitService`/`MemberVisitStore`
- the manual “record Workout” creation flow and Member-entered date/start/end controls
- stale tests and documentation that describe Visits and Workouts as separate Member records

Do not delete Owner visit functionality merely because the Member tab is removed. Move it to the unified store first.

## Phase 0 - Establish a safety baseline and prepare the reset

### Goal

Record the current green baseline, prepare a recoverable database reset, and make the confirmed decisions explicit in the product contract before modifying persistence.

### Steps

1. Update the affected Member and Owner stories in `docs/UserStories.md` on the implementation branch, including empty Workouts and immutable Member session times.
2. Inventory all production, test, rendered-UI, guide, architecture, and log references to Visits, Workouts, `MEMBER_VISITS`, manual recording, Member deletion, and the one-set minimum.
3. Run the existing unit, rendered UI, and Checkstyle tasks and record unrelated pre-existing failures.
4. Resolve and record the exact live development database path. Do not treat test databases or repository source files as reset targets.
5. Make a timestamped backup outside the reset target and verify that it exists and is non-empty.
6. Immediately before the destructive operation, show the resolved source and backup paths and request confirmation; then use the application's reset mechanism or remove only that verified database file.
7. Remove the project-local `.gradle` cache before handoff without touching the normal user-level Gradle cache.

### Tests and gate

- `gradlew.bat test renderedUiTest checkstyleMain` passes, or every pre-existing failure is recorded.
- Acceptance tests are named for check-in creation, live exercise editing, empty check-out, strict positive duration, immutable times, non-deletable Workouts, Owner oversight, and unified history.
- The database backup and reset targets are explicitly verified before any destructive cleanup.

## Phase 1 - Introduce the clean unified session model

### Goal

Make Workout the sole persisted session aggregate. Existing development rows are intentionally not imported because Visit/Workout equivalence cannot be established safely.

### Steps

1. Evolve `Workout` so `endedAt` is nullable while the session is open; add correction metadata if Owner oversight is retained.
2. Permit an empty immutable set list in `Workout` and `SaveWorkoutRequest`; split commands if clearer:
   - a check-in command with no Member-supplied times;
   - an exercise/notes update command with no times; and
   - a check-out command that can atomically persist the current draft.
3. Define the latest clean `workouts` schema with nullable `ended_at`, strict completed-range constraints where enforceable, correction metadata, ordered child sets, and a partial unique index for one open Workout per Member.
4. Remove `visits` and Visit-only indexes from the latest schema; do not add a legacy import or timestamp-based deduplication routine.
5. Remove the one-set minimum from service validation and database assumptions while retaining per-set validation.
6. Change reset/schema initialization and schema-version documentation.
7. Retain only migration tests still relevant to supported pre-unification schema changes; replace conflicting legacy Visit/Workout merge tests with clean initialization and reset coverage.

### Tests

- A new database contains the unified schema, foreign keys, child cascade, and partial unique-open index.
- Clean initialization creates no `visits` table and creates exactly one unified Workout schema.
- The verified database reset starts without legacy Visit or Workout rows.
- Empty and non-empty Workouts round-trip and persist across restart.
- Two open Workouts for one Member are rejected at the database level.
- A Member can have many completed Workouts, including multiple on one date.
- Factory reset removes unified sessions and sets.
- Relevant supported migrations still roll back on failure and do not advance `PRAGMA user_version`.

### Phase gate

All updated `GymFlowDatabaseTest` initialization/reset cases pass before service callers are switched, and no test expects ambiguous legacy Visit/Workout merging.

## Phase 2 - Consolidate transactional services and authorization

### Goal

Provide one service/store API for check-in, exercise editing, check-out, history, and Owner oversight.

### Steps

1. Move Membership eligibility and active-Member checks into the unified Workout transaction path.
2. Implement check-in as one transaction: validate actor and Membership, reject an existing open Workout, and insert a zero-exercise open Workout at `clock.instant()`.
3. Load current state from the open Workout instead of `visits`.
4. Implement an actor-owned update that replaces notes and ordered sets without accepting Member-supplied session times.
5. Implement check-out as one transaction that finds the owned open Workout, validates and replaces the submitted draft, sets `ended_at` from the clock, and returns the completed Workout.
6. Keep service-side duplicate-action protection so rapid clicks and stale screens remain safe.
7. Repoint Owner history/search/correction services to unified Workouts and keep Owner correction transactional.
8. Remove Visit service/store methods only after all Member and Owner callers use the unified API. Rename types where it improves clarity; avoid compatibility wrappers that perpetuate two domain models.

### Tests

- Eligible check-in creates exactly one open zero-exercise Workout with the clock time.
- Cancelled UI confirmation never calls the service (covered in Phase 3 UI tests).
- Missing/expired/upcoming/deactivated Membership prevents check-in without creating a Workout.
- Concurrent or repeated check-in cannot create a second open Workout.
- Check-out succeeds after Membership expiry/deactivation and uses the clock time.
- Check-out at a clock value equal to or earlier than the start is rejected and leaves the Workout open.
- Check-out without an open Workout changes nothing.
- A check-out persistence/validation failure leaves the session open and its prior children unchanged.
- Empty exercise lists are valid; invalid individual set rows are still rejected.
- Member exercise edits cannot alter start/end times or another Member's Workout.
- Owner search and correction see the same records as Member history.
- Owner correction cannot create a negative interval or a second open Workout.
- Authorization rejects null, inactive, Owner-as-Member, and cross-Member actors.

### Phase gate

Service, concurrency/constraint, ownership, rollback, and Owner regression tests pass with no production reads or writes to `visits`.

## Phase 3 - Refactor Member Home into the active Workout workspace

### Goal

Let a Member safely start/finish a Workout and manage its exercise draft from Home, using existing application styles.

### Steps

1. Add a confirmation dialog before check-in. It clearly says that confirming starts and records a Workout now; Cancel is the safe/default action.
2. Add a confirmation dialog before check-out. It clearly says that confirming saves the displayed exercises and ends the Workout now; Cancel retains the active form.
3. While checked out, show the existing membership/status presentation and only the check-in action; do not show a stale exercise editor.
4. While checked in, display the start time, elapsed/current status, check-out action, notes, and the reusable exercise/set editor immediately below the status/action area.
5. Reuse or extract the current Workout exercise-row controls and validation rather than creating a second visual/form implementation.
6. Save in-progress notes/exercises explicitly or on controlled edits according to the chosen UX; disable actions while a request is running and restore them on failure.
7. On confirmed check-out, submit the current draft transactionally, then refresh Home and Workout history.
8. Keep focus order, accessible names, keyboard operation, loading/error states, and both themes consistent with existing screens.

### Tests

- Check-in and check-out each require an affirmative confirmation.
- Cancelling either dialog performs no write and preserves the current screen/form.
- Confirmed check-in shows an open Workout editor with the recorded start time.
- Exercises can be added, removed, reordered, and edited on Home while checked in.
- A Member can check out with no exercises.
- Confirmed check-out uses the service clock rather than a form-entered time.
- Double-clicks or stale actions do not create/close twice.
- Errors keep the user checked in and keep unsaved form values recoverable.
- Keyboard focus enters the dialog, returns sensibly after cancel, and all controls have accessible labels.
- Rendered tests cover checked-out, checked-in empty, checked-in populated, validation-error, light, and dark states.

### Phase gate

Member Home is fully usable for the active Workout without opening another screen, and no manual time input is exposed.

## Phase 4 - Remove duplicate Member Visit navigation

### Goal

Expose one Member `Workouts` tab as the sole Member history route. A Workout is the gym visit record, including
an empty Workout. This phase removes only the duplicate `Gym Visits` tab and its Member-only code path; it does
not remove or redesign existing `Workouts` UI, actions, editing, calendar/card presentation, or body-mass routing.

### Steps

1. Remove `Gym Visits` from `MemberHomeView.NAVIGATION`, screen-title mappings, route mappings, and authorization tests.
2. Remove `MEMBER_VISITS` from `Screen` and `AppView` after all direct links/callers are gone.
3. Retain `MemberWorkoutsView` and all of its existing capabilities unchanged; it already remains the authoritative
   Member history for both empty and exercise-filled Workouts.
4. Preserve the current calendar/card style and deterministic ordering, but handle zero exercises with a clear “No exercises recorded” summary.
5. Do not remove the existing “record Workout” button, Member date/start/end fields, deletion action, or
   notes/exercise editing controls in this phase; they are existing Workout capabilities, not Gym Visits code.
6. Do not remove Workout service entry points as part of this navigation cleanup. A later, separately scoped
   domain/API change may revise those capabilities with dedicated migration and regression coverage.
7. Keep body-mass navigation reachable from `Workouts` without changing its existing route or controls.
8. Delete `MemberVisitsView` and its focused tests once unified history coverage is confirmed. Do not remove or
   alter Owner `Visits` functionality or shared code still needed by check-in/check-out.

### Tests

- Member navigation contains exactly one `Workouts` entry and no `Gym Visits` entry.
- Removed routes cannot be reached through `AppView`.
- History includes sessions created by check-in/out after the clean reset.
- Empty Workouts render without exceptions or misleading exercise text.
- Open and completed states have distinct, accessible labels.
- Calendar grouping and cross-midnight behavior use the agreed session date (retain current end-date grouping for completed sessions unless requirements change).
- Existing Workout-history controls and behaviours remain unchanged, including its current actions, time controls,
  notes/exercise editing, and empty/open/completed Workout presentation.
- Body-mass routing and existing Member/Owner authorization continue to work.

### Phase gate

There is no Member-visible duplicate history or second route, and source search finds no stale `MEMBER_VISITS`,
`MemberVisitsView`, or Member `Gym Visits` navigation references. Existing Workout functionality is unchanged.

## Phase 5 - Remove stale code and update the product contract

### Goal

Finish the refactor without dead abstractions, misleading documentation, or tests that enforce superseded behavior.

### Steps

1. Remove only Member Visit-only code after confirming it has no check-in, check-out, or Owner callers. Do not
   remove shared Visit model/store/service code merely because the Member tab is gone.
2. Delete stale tests asserting that Members navigate to `Gym Visits` or have a separate Member Visit history;
   preserve tests for the current Workout UI and all its existing actions.
3. Replace those tests with unified behavior tests; do not delete useful ownership, rollback, correction, date-boundary, or formatting coverage.
4. Update `README.md`, `docs/UserGuide.md`, `docs/DeveloperGuide.md`, `docs/Architecture.md`/`ARCHITECTURE.md`, `docs/UserStories.md`, and project decisions as applicable.
5. Search resources, rendered tests, screenshots/text assertions, and CSS for obsolete labels and unused selectors.
6. Run Checkstyle and remove all Java violations as required by `config/checkstyle/checkstyle.xml`.

### Initial manual-test account setup after the reset

At the first post-reset test session, guide the user through setup in this order. Let the application create records through supported UI flows; do not seed credentials in source code or commit them.

1. Create the initial Owner account and sign in.
2. From the Owner workflow, create a primary Member with a Membership active on the test date. Use this account for successful check-in, exercise editing, check-out, history, restart, and Owner correction tests.
3. Create a Member with an expired Membership for rejected check-in coverage.
4. Create a Member with an upcoming Membership for rejected check-in coverage.
5. Create a Member whose Membership can be deactivated by the Owner after check-in, then verify that check-out still succeeds.
6. Record the purpose of each test account without recording plaintext passwords. The user chooses and retains the credentials.
7. Confirm each Member sees only their own Workouts and that the Owner sees all unified sessions in `Visits`.

### Final verification

1. Run `gradlew.bat clean check` and the configured rendered-UI test task.
2. Test clean initialization and reset, and verify the pre-reset development database backup remains available for recovery.
3. Manually verify:
   - check-in confirm/cancel;
   - live empty and populated exercise editing on Home;
   - check-out confirm/cancel;
   - membership expiry between check-in and check-out;
   - rapid repeated clicks;
   - restart while checked in;
   - attempted immediate/equal-time check-out rejection followed by a successful later check-out;
   - unified Workout history;
   - Owner search/correction of the same session;
   - light/dark themes, keyboard navigation, and accessible labels.
4. Run `rg` for `MEMBER_VISITS`, `MemberVisitsView`, and Member `Gym Visits` navigation labels. Every remaining
   match must be intentional; existing Workout controls are not stale solely for this cleanup.
5. Confirm no project-local Gradle cache or other generated artifact is staged.

## Completion criteria

- One confirmed check-in creates exactly one Workout; one confirmed check-out completes it.
- Start/end times cannot be manually supplied by a Member.
- Empty Workouts are valid and visible in history.
- Exercises can be managed beneath the active check-in area on Member Home.
- Member navigation and history contain only `Workouts`, not `Gym Visits`.
- Owner oversight remains functional against the same authoritative data.
- The old development database is backed up and deliberately reset; no ambiguous legacy rows are silently imported.
- The duplicate Member Visit route, its Member-only code, tests, documentation, and labels are removed without
  removing existing Workout capabilities.
- Unit, integration, migration, rendered-UI, authorization, Checkstyle, and full Gradle checks pass.

## Risks and mitigations

- **Ambiguous legacy duplicates:** back up and reset the development database; never guess or silently merge rows.
- **Loss of attendance accountability:** make Member times immutable and preserve Owner correction metadata.
- **Partial check-out save:** update exercises and close the session in one transaction.
- **Concurrent actions:** combine service checks with a database unique constraint and guarded updates.
- **UI state loss:** keep drafts on cancel/failure and refresh only after successful completion.
- **Large-bang refactor:** land schema, services, Home UI, navigation cleanup, and deletion behind phase gates so each layer is testable.
