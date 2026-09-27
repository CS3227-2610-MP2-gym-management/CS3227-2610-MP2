# GymFlow User Stories

This document records the agreed product backlog and its relationship to the current release. It is a planning and
traceability document; the [User Guide](UserGuide.md) remains the source of truth for behaviour available to testers.

## Status legend

| Status      | Meaning                                                                               |
| ----------- | ------------------------------------------------------------------------------------- |
| Implemented | Available in the current application                                                  |
| Partial     | A shared contract or narrower version exists, but the complete story is not available |
| Planned     | Not implemented in the current release                                                |

Owner stories are Dylan's primary scope. Member-facing stories belong to Isaac for the Member role.
Shared storage and service contracts may exist before the corresponding Member interface is implemented.

## Member stories

### P0 — Must have

| ID      | User story                                                                                                              | Status  | Notes                                                                               |
| ------- | ----------------------------------------------------------------------------------------------------------------------- | ------- | ----------------------------------------------------------------------------------- |
| M-P0-01 | As a gym Member, I want to log in securely so that I can access my personal gym account.                                | Implemented | Active Member credentials authenticate through the normal login form and open Member-only routes. |
| M-P0-02 | As a gym Member, I want to view my profile and Membership details so that I can confirm that my information is correct. | Implemented | Member-only account reads expose the authenticated profile and complete Membership history. |
| M-P0-03 | As a gym Member, I want to view my Membership dates and current status so that I know whether I may use the gym.        | Implemented | Membership status is derived from the active flag and inclusive dates using the local clock. |
| M-P0-04 | As a gym Member, I want to check in so that a Workout starts when I arrive.                                              | Planned | Check-in creates one open Workout only for an active Member with a valid, inclusive-date Membership. |
| M-P0-05 | As a gym Member, I want to check out so that my Workout ends when I depart.                                              | Planned | Check-out completes only the Member's own open Workout, even after Membership changes; it requires at least one full minute after check-in. |
| M-P0-06 | As a gym Member, I want to see whether I am checked in so that I do not submit a duplicate check-in or check-out.       | Planned | Current state is derived from an open Workout; only the valid action is enabled.     |
| M-P0-07 | As a gym Member, I want to view my Workout history so that I can review my gym sessions.                                | Planned | The unified history includes empty and completed Workouts owned by the Member.       |
| M-P0-08 | As an existing Member, I want to know when renewal is needed and where to renew so that I can restore gym access.        | Implemented | A prominent Home notice and My Membership guidance direct Members without a current or upcoming Membership to visit in person; the Owner records the Membership and Payment. |

### P1 — Should have

| ID      | User story                                                                                                                    | Status  | Notes |
| ------- | ----------------------------------------------------------------------------------------------------------------------------- | ------- | ----- |
| M-P1-01 | As a gym Member, I want to record completed workout sessions so that I can track each period of exercise activity.            | Planned | Check-in and check-out supply the immutable times; multiple completed Workouts may share a date, and an empty Workout is valid. |
| M-P1-02 | As a gym Member, I want to record exercises and ordered sets using repetitions or duration and optional resistance so that I can monitor training progress. | Planned | A Workout has zero or more ordered sets. Named exercises may retain an incomplete draft set; completed sets have exactly one positive measure and optional non-negative resistance. |
| M-P1-03 | As a gym Member, I want to view and edit my previous workouts so that my personal exercise history remains useful and accurate. | Planned | Members may edit notes and exercises only; they cannot edit session times or delete Workouts, and cannot access another Member's records. |
| M-P1-04 | As a gym Member, I want to record, edit, and delete body-mass readings so that I can monitor fitness progress.                 | Implemented | Each Member may store one positive kilogram reading per calendar date; future-dated readings are not allowed. |
| M-P1-05 | As a gym Member, I want to view Owner announcements so that I stay informed about gym operations.                             | Planned | Members see published announcements only; read/unread tracking is not required. |
| M-P1-06 | As a gym Member, I want to update selected profile details so that my contact information remains current.                    | Implemented | Self-service editing is limited to email address and phone number. |
| M-P1-07 | As a gym Member, I want to change my password so that I can keep my account secure.                                           | Implemented | The Member must provide the current password and a matching new password of 12–128 characters. |

### P2 — Could have

| ID      | User story                                                                                           | Status  | Notes |
| ------- | ---------------------------------------------------------------------------------------------------- | ------- | ----- |
| M-P2-01 | As a gym Member, I want to view workout and body-weight trends so that I can understand my progress. | Planned | Trends show completed-Workout frequency, per-Workout load volume, and body-mass readings. Load volume sums repetitions multiplied by resistance; timed and unweighted sets contribute zero. |

## Owner stories

### P0 — Must have

| ID      | User story                                                                                                                       | Status      | Notes                                                                                        |
| ------- | -------------------------------------------------------------------------------------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------------- |
| O-P0-01 | As a gym Owner, I want to log in securely so that I can access administrative features.                                          | Implemented | Includes first-run Owner setup and logout.                                                   |
| O-P0-02 | As a gym Owner, I want to create a Member account so that a new Member can access the application.                               | Implemented | The Owner chooses the initial password.                                                      |
| O-P0-03 | As a gym Owner, I want to view and search Members so that I can quickly find their records.                                      | Implemented | Search matches name or email.                                                                |
| O-P0-04 | As a gym Owner, I want to edit a Member profile so that I can correct or update its information.                                 | Implemented | Editing does not rewrite historical records.                                                 |
| O-P0-05 | As a gym Owner, I want to deactivate a Member's Membership so that they can no longer enter the gym while retaining access to their past records. | Planned | Deactivation does not disable the Member account, delete history, or close an existing Workout. |
| O-P0-06 | As a gym Owner, I want to activate a Membership with start and expiry dates so that a Member can use the gym during that period. | Implemented | Active periods for one Member cannot overlap.                                                |
| O-P0-07 | As a gym Owner, I want to renew a Membership so that a Member can continue using the gym.                                        | Implemented | Renewal creates a new immutable Membership and Payment rather than extending history.        |
| O-P0-08 | As a gym Owner, I want to record a Membership Payment so that the gym has accurate income history.                               | Implemented | Each Membership purchase has exactly one Payment.                                            |
| O-P0-09 | As a gym Owner, I want to view a Member's Payment history so that I can verify what was paid.                                    | Implemented | Available from the Member profile and global Finances page.                                  |
| O-P0-10 | As a gym Owner, I want to view one Member's Workout history so that I can review individual attendance.                        | Planned | Available from the Member profile and uses the authoritative Workout records.               |
| O-P0-11 | As a gym Owner, I want to view all Workout records so that I can monitor collective attendance.                                | Planned | Includes all and currently-active views of the authoritative Workout records.               |
| O-P0-12 | As a gym Owner, I want entry without a valid Membership to be rejected so that access rules are enforced.                        | Partial     | `hasValidMembership(memberId, date)` exists; Member entry submission remains teammate-owned. |

### P1 — Should have

| ID      | User story                                                                                                    | Status      | Notes                                                                                  |
| ------- | ------------------------------------------------------------------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------- |
| O-P1-01 | As a gym Owner, I want to correct inaccurate Workout times so that attendance records remain accurate.        | Planned | The latest correction records its Owner, time, and reason; Members cannot change those times. |
| O-P1-02 | As a gym Owner, I want to create, edit, and deactivate Membership plans so that I can offer reusable options. | Planned     | Memberships currently use explicit dates and Payment amounts.                          |
| O-P1-03 | As a gym Owner, I want to assign a Membership plan so that a Member receives its conditions.                  | Planned     | No MembershipPlan entity exists.                                                       |
| O-P1-04 | As a gym Owner, I want to publish and withdraw announcements so that Members receive current information.     | Implemented | Owner management and the shared published-list query exist; Member display is planned. |
| O-P1-05 | As a gym Owner, I want to view basic attendance statistics so that I can understand gym usage.                | Partial     | The dashboard shows the current visitor count but no broader trends.                   |
| O-P1-06 | As a gym Owner, I want to view all-time revenue so that I can monitor Membership income.                      | Implemented | The dashboard also shows Expenses and net income.                                      |
| O-P1-07 | As a gym Owner, I want to reset a Member password so that I can restore account access.                       | Implemented | Reset creates a fresh PBKDF2 salt and hash.                                            |
| O-P1-08 | As a gym Owner, I want to record Expenses so that the gym has accurate expenditure history.                   | Implemented | Expenses are immutable and filterable by category.                                     |

### P2 — Could have

| ID      | User story                                                                                                          | Status  |
| ------- | ------------------------------------------------------------------------------------------------------------------- | ------- |
| O-P2-01 | As a gym Owner, I want to export Member, Payment, or Workout records as CSV files for external analysis or archiving. | Planned |
| O-P2-02 | As a gym Owner, I want to identify peak usage periods so that I can make operational decisions.                     | Planned |
| O-P2-03 | As a gym Owner, I want to view an audit history of important administrative changes.                                | Planned |

## Scope decisions

- One installation represents one gym and supports one Owner account.
- Currency is fixed to SGD.
- Membership validity and display status are derived from the active flag and dates rather than stored as permanent
  status values.
- Membership and Payment purchase history is immutable. Renewal creates another Membership and Payment.
- A Membership deactivated while its Member is inside does not invent an end time or close the existing Workout.
- A Workout is the authoritative gym-session record. Members may change its notes and exercise sets but not its
  start/end times or delete it; an empty Workout is valid.
- CSV exports are snapshots of the currently displayed Member, Payment, or Workout records after search and tab filters.
- Member-facing login, dashboards, attendance submission, workouts, and body metrics remain outside Dylan's Owner scope.
