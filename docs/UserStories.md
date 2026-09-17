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
| M-P0-01 | As a gym Member, I want to log in securely so that I can access my personal gym account.                                | Planned | Member accounts and credentials exist, but Member login routing is not implemented. |
| M-P0-02 | As a gym Member, I want to view my profile and Membership details so that I can confirm that my information is correct. | Planned | Owner-visible profile records already provide the shared data.                      |
| M-P0-03 | As a gym Member, I want to view my Membership dates and current status so that I know whether I may use the gym.        | Planned | Membership status is derived by the shared model.                                   |
| M-P0-04 | As a gym Member, I want to record my arrival so that the beginning of my Visit is retained.                             | Planned | The shared Visit schema supports an open Visit.                                     |
| M-P0-05 | As a gym Member, I want to record my departure so that the end of my Visit is retained.                                 | Planned | The shared Visit schema supports completing an open Visit.                          |
| M-P0-06 | As a gym Member, I want to see whether I am checked in so that I do not submit a duplicate entry or exit.               | Planned | Current state is derived from whether an open Visit exists.                         |
| M-P0-07 | As a gym Member, I want to view my Visit history so that I can review previous attendance.                              | Planned | Owner-visible history already provides the shared query model.                      |
| M-P0-08 | As a potential or existing Member, I want to purchase or renew a Membership so that I can access the gym.               | Planned | Owners currently create Membership purchases and renewals.                          |

### P1 — Should have

| ID      | User story                                                                                                          | Status  |
| ------- | ------------------------------------------------------------------------------------------------------------------- | ------- |
| M-P1-01 | As a gym Member, I want to record a completed workout so that I can track my exercise activity.                     | Planned |
| M-P1-02 | As a gym Member, I want to record exercises, sets, repetitions, and weight so that I can monitor training progress. | Planned |
| M-P1-03 | As a gym Member, I want to view previous workouts so that I can compare my performance over time.                   | Planned |
| M-P1-04 | As a gym Member, I want to record body weight so that I can monitor fitness progress.                               | Planned |
| M-P1-05 | As a gym Member, I want to view Owner announcements so that I stay informed about gym operations.                   | Planned |
| M-P1-06 | As a gym Member, I want to update selected profile details so that my contact information remains current.          | Planned |
| M-P1-07 | As a gym Member, I want to change my password so that I can keep my account secure.                                 | Planned |

### P2 — Could have

| ID      | User story                                                                                           | Status  |
| ------- | ---------------------------------------------------------------------------------------------------- | ------- |
| M-P2-01 | As a gym Member, I want to view workout and body-weight trends so that I can understand my progress. | Planned |

## Owner stories

### P0 — Must have

| ID      | User story                                                                                                                       | Status      | Notes                                                                                        |
| ------- | -------------------------------------------------------------------------------------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------------- |
| O-P0-01 | As a gym Owner, I want to log in securely so that I can access administrative features.                                          | Implemented | Includes first-run Owner setup and logout.                                                   |
| O-P0-02 | As a gym Owner, I want to create a Member account so that a new Member can access the application.                               | Implemented | The Owner chooses the initial password.                                                      |
| O-P0-03 | As a gym Owner, I want to view and search Members so that I can quickly find their records.                                      | Implemented | Search matches name or email.                                                                |
| O-P0-04 | As a gym Owner, I want to edit a Member profile so that I can correct or update its information.                                 | Implemented | Editing does not rewrite historical records.                                                 |
| O-P0-05 | As a gym Owner, I want to deactivate a Member's Membership so that they can no longer enter the gym while retaining access to their past records. | Implemented | Deactivation does not disable the Member account, delete its history, or close an existing Visit. |
| O-P0-06 | As a gym Owner, I want to activate a Membership with start and expiry dates so that a Member can use the gym during that period. | Implemented | Active periods for one Member cannot overlap.                                                |
| O-P0-07 | As a gym Owner, I want to renew a Membership so that a Member can continue using the gym.                                        | Implemented | Renewal creates a new immutable Membership and Payment rather than extending history.        |
| O-P0-08 | As a gym Owner, I want to record a Membership Payment so that the gym has accurate income history.                               | Implemented | Each Membership purchase has exactly one Payment.                                            |
| O-P0-09 | As a gym Owner, I want to view a Member's Payment history so that I can verify what was paid.                                    | Implemented | Available from the Member profile and global Finances page.                                  |
| O-P0-10 | As a gym Owner, I want to view one Member's Visit history so that I can review individual attendance.                            | Implemented | Available from the Member profile.                                                           |
| O-P0-11 | As a gym Owner, I want to view all Visit records so that I can monitor collective attendance.                                    | Implemented | Includes all and currently-visiting views.                                                   |
| O-P0-12 | As a gym Owner, I want entry without a valid Membership to be rejected so that access rules are enforced.                        | Partial     | `hasValidMembership(memberId, date)` exists; Member entry submission remains teammate-owned. |

### P1 — Should have

| ID      | User story                                                                                                    | Status      | Notes                                                                                  |
| ------- | ------------------------------------------------------------------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------- |
| O-P1-01 | As a gym Owner, I want to correct inaccurate Visit times so that attendance records remain accurate.          | Implemented | The latest correction records its Owner, time, and reason.                             |
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
| O-P2-01 | As a gym Owner, I want to export Member, Payment, or Visit records as CSV files for external analysis or archiving. | Planned |
| O-P2-02 | As a gym Owner, I want to identify peak usage periods so that I can make operational decisions.                     | Planned |
| O-P2-03 | As a gym Owner, I want to view an audit history of important administrative changes.                                | Planned |

## Scope decisions

- One installation represents one gym and supports one Owner account.
- Currency is fixed to SGD.
- Membership validity and display status are derived from the active flag and dates rather than stored as permanent
  status values.
- Membership and Payment purchase history is immutable. Renewal creates another Membership and Payment.
- A Membership deactivated while its Member is inside does not invent an exit time or close the existing Visit.
- Member-facing login, dashboards, attendance submission, workouts, and body metrics remain outside Dylan's Owner scope.
