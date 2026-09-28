# Member Feature Membership Gating

**Date:** 29 September 2026  
**Status:** Completed; Supabase execution pending a running local instance

## Scope

Investigated the report that a Member without a current Membership could still record Workouts and body-mass
readings. Extended the fix after follow-up feedback so check-in and check-out are also unavailable without an active
Membership, with a themed explanation shown on every affected screen.

## Findings

- `Record Workout` and `Save reading` were always enabled after their screens loaded.
- `WorkoutService.create` and `BodyMetricService.create` required only an active Member account, not a current
  Membership.
- Supabase insert policies likewise allowed an authenticated Member to create their own Workout or body-mass row
  without checking Membership dates and activation state.
- Member Home derived check-in and check-out availability only from the open-Workout state.
- Workout history and update operations were already separate from creation, allowing the requested read and edit
  access to be preserved.

## Changes

- Added `MemberAccountService.hasCurrentMembership` as the shared UI-facing Membership check.
- Disabled `Record Workout`, `Save reading`, `Check in`, and `Check out` when the Member has no Membership that is
  active on the current date.
- Added a reusable yellow notice to Member Home, Workouts, and Body Mass:
  `You do not have an active membership. Please renew your membership to access this feature.`
- Added light-mode and dark-mode styles for the notice using the application's existing amber Membership palette.
- Enforced the creation rule in `WorkoutService` and `BodyMetricService`, while leaving history and update operations
  available without a current Membership.
- Added a Supabase migration with insert triggers for Workouts and body-mass readings so direct requests cannot bypass
  the application checks.
- Added Java regression coverage for creation rejection, retained history/edit access, Home button-state rules, and
  the explanatory message.
- Added a pgTAP regression test covering the corresponding Supabase behavior.

## Verification

- `gradlew.bat test` passed.
- `gradlew.bat checkstyleMain checkstyleTest` passed.
- Focused `MemberHomeViewTest` execution passed after adding the Home-state assertions.
- `git diff --check` passed.
- The new Supabase migration and pgTAP test were added but were not executed because a running local Supabase instance
  was not available during this work.

## Suggested commit message

`fix(member): require active membership for tracking features`
