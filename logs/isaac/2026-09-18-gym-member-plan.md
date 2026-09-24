# Gym Member Implementation Plan Interaction Summary

## Goal

Create a phased execution plan for every Member-facing feature in `docs/UserStories.md`, beginning with Isaac's
2:00 AM request. The plan must enforce P0 before P1 and P1 before P2, describe success and edge cases, list reference
files, provide English test cases and sequence diagrams, and give simple step-by-step implementation instructions for
each feature.

## Important prompts and decisions

- Isaac requested a `gym-member` plan under `docs/Plans/`, based on the Member stories in `docs/UserStories.md`.
- Each feature needed an explicit goal, implementation sequence, context files, new classes or entities, edge cases,
  unit and other important tests in English, and a sequence diagram.
- The plan needed to follow SRP, DRY, layered architecture, and the project's existing implementation decisions rather
  than introducing unnecessary abstractions.
- Member Membership purchase and renewal was clarified as informational only. When renewal is needed, the Member is
  instructed to visit the gym in person; the application does not process payment, contact the Owner, or create a
  purchase request.
- Self-service profile editing was limited to email and phone number. Password changes require the current password
  and a matching new password confirmation.
- A completed Workout is a distinct session. Members may complete multiple Workouts on one date, but every saved
  Workout requires at least one exercise set and there are no draft Workouts.
- Each Workout set records an exercise name and exactly one positive measure: repetitions or duration. Resistance in
  kilograms is optional and cannot be negative, allowing both weighted and bodyweight exercises.
- Members may view, edit, and permanently delete their own Workouts and sets.
- Body-mass tracking is separate from bodyweight exercise. A Member may keep one positive kilogram reading per date
  and may edit or delete it.
- P2 trends were defined as completed-Workout frequency, per-Workout load volume, and body-mass history. Load volume
  sums repetitions multiplied by resistance; timed and unweighted sets contribute zero.
- Isaac requested that the plan be left unchanged temporarily, then asked for it to be saved as Markdown, and finally
  requested that the identified User Story discrepancies be recorded in the backlog.

## Skills and tools

No specialized skill was required. Repository inspection covered the architecture contract, product decisions, User
Stories, User Guide, Developer Guide, current services, stores, models, JavaFX routes, schema, and automated tests.
PowerShell and repository search were used for read-only discovery. `apply_patch` created and updated the Markdown
files.

## Planning work

- Grouped the 16 Member stories into 10 dependency-ordered implementation features while preserving traceability to
  every story ID.
- Defined five P0 features: authentication, profile and Membership display, check-in/check-out state, Visit history,
  and in-person renewal guidance.
- Defined four P1 features: Workout CRUD/history, body-mass tracking, published announcements, and contact/password
  management.
- Defined the P2 progress feature using derived rather than persisted trend data.
- Added P0 and P1 completion gates so later phases cannot begin before the earlier phase is fully tested.
- Identified focused models, services, stores, projections, UI routes, database constraints, authorization boundaries,
  transaction boundaries, background-task requirements, and documentation updates.
- Expanded every feature with a literal ordered implementation procedure suitable for direct execution by another
  coding agent.
- Included one Mermaid sequence diagram and one English test/edge-case section for every feature.

## User Story corrections

- Replaced M-P0-08's implied Member purchase workflow with in-person renewal guidance and Owner-recorded Membership
  and Payment handling.
- Clarified multiple completed Workouts per day and the absence of drafts in M-P1-01.
- Expanded M-P1-02 to cover ordered repetition-based or duration-based sets with optional resistance.
- Expanded M-P1-03 from read-only history to Member-owned Workout and set editing and deletion.
- Clarified one editable body-mass reading per Member per date in M-P1-04.
- Recorded published-only announcement visibility with no read/unread tracking for M-P1-05.
- Limited M-P1-06 to email and phone changes.
- Added current-password verification and the existing 12-128-character policy to M-P1-07.
- Defined the three calculated trend series for M-P2-01.
- All affected stories remain `Planned`; no implementation status was advanced prematurely.

## Affected components

- Added `docs/Plans/gym-member.md`.
- Updated the Member story tables in `docs/UserStories.md`.
- Added this interaction summary at `logs/isaac/2026-09-18-gym-member-plan.md`.
- No Java source, database schema, dependency, or automated-test files were changed.

## Test evidence

- The plan was structurally checked and contains 10 feature sections, 10 step-by-step implementation sections, 10
  English test/edge-case sections, and 10 correctly closed Mermaid sequence-diagram blocks.
- `git diff --check` reported no Markdown whitespace errors; Git emitted only the repository's existing LF-to-CRLF
  conversion warning for `docs/UserStories.md`.
- Application tests were not run because this interaction changed documentation only.

## Commit or PR references

No commit or pull request was created during this interaction.

## Human review status

Pending Isaac review. Isaac should verify that the revised Member stories and the implementation steps in
`docs/Plans/gym-member.md` accurately reflect the agreed feature behavior before implementation begins.
