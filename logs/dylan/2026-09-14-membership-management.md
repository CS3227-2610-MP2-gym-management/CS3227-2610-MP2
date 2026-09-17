# Owner Membership Management

## Goal

Implement Owner-side Membership management and a shared validity contract without implementing Member-facing flows.

## Prompt and interaction summary

- Confirmed that Membership deactivation must not disable the Member account or prevent login.
- Kept the established `Account` and `Role` Java names and documented their mapping to `User` and `UserRole`.
- Chose one immutable Membership and one Payment per purchase.
- Rejected overlap with active periods while allowing replacement of deactivated periods.
- Added a Memberships overview plus Membership history and activation controls on Member profiles.
- Used failing tests for legacy schema migration, account timestamps, Membership creation, overlap, validity, and screen
  navigation before implementation.

## Agent skills and tools

- Superpowers brainstorming, executing-plans, TDD, and verification
- Ponytail Full for minimal service and persistence additions
- JUnit integration tests against temporary SQLite databases
- Gradle, Checkstyle, release-JAR verification, and Git

## Important decisions

- `accounts.is_active` remains the login flag; Membership access uses `memberships.is_active` and its dates.
- Membership status is derived and is never stored.
- Expired Memberships cannot be reactivated; the Owner adds a new purchase instead.
- `OwnerMemberService.hasValidMembership(memberId, date)` is the shared contract for the teammate's Visit flow.
- Existing databases receive required timestamp columns through an idempotent migration without losing records.
- Plans, tiers, refunds, Payment editing, Visits, and Member UI remain out of scope.
