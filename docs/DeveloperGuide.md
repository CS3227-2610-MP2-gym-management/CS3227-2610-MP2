# GymFlow Developer Guide

## Architecture

GymFlow is a modular Java SE 25 and JavaFX 25 desktop application. It uses a single local SQLite database and one
JavaFX `Scene`; `AppView` replaces the scene root when navigating so screen changes do not create extra windows.

The current code is divided by responsibility:

- `com.gymflow.ui`: application startup, navigation, and JavaFX views.
- `com.gymflow.auth`: password hashing and authentication rules.
- `com.gymflow.data`: SQLite initialization and account persistence.
- `com.gymflow.model`: shared account and role data.
- `com.gymflow.member`: Owner-side Member onboarding and profile rules.

Concrete classes are used instead of repository interfaces or factories because each responsibility currently has one
implementation. New abstractions should be introduced only when a second implementation or a real testing boundary
requires one.

## Authentication

An installation supports one Owner account. On first launch, `GymFlowApp` initializes `data/gymflow.db` and the Login
screen switches to setup mode when no Owner exists.

Passwords are hashed with PBKDF2-HMAC-SHA256 using 600,000 iterations, a random 16-byte salt, and a 32-byte derived
hash. The database stores the Base64-encoded hash and salt, never the plain-text password. Verification uses a
constant-time hash comparison. Authentication and reset database work run outside the JavaFX application thread.

## Persistence

`GymFlowDatabase` owns schema initialization and full reset. SQLite foreign-key enforcement is enabled for each
connection. The current schema contains `accounts` with:

- a case-insensitively unique normalized email;
- password hash, salt, and iteration count;
- `OWNER` or `MEMBER` role;
- active flag plus creation and update timestamps;
- a partial unique index allowing only one Owner.

Reset discovers all non-SQLite tables and recreates the centralized schema inside one transaction. If recreation
fails, SQLite rolls back the operation rather than leaving a partially cleared database.

Member onboarding adds `member_profiles`, `memberships`, and `payments`. `member_profiles.account_id` is both its
primary key and a foreign key to a `MEMBER` account. Each initial Membership belongs to one profile, and its Payment is
linked by a unique membership ID. Payment amounts are stored as integer SGD cents.

`OwnerMemberStore` creates the account, generated member number, profile, initial Membership, and Payment in one SQLite
transaction. `OwnerMemberService` validates input and clears the caller's password array. Member search escapes SQL
wildcards and matches name or email without regard to case. Payment history is loaded through the Membership link:
`member_profiles.account_id` to `memberships.member_account_id` to `payments.membership_id`.

Member profile validation is centralized in `OwnerMemberService`. Phone numbers are limited to eight-digit Singapore
numbers beginning with `3`, `6`, `8`, or `9` and normalized to `+65 XXXX XXXX`. An optional date of birth must make the
Member at least 12 years old. JavaFX dialogs provide immediate input restrictions, but the service remains the
authoritative boundary. Dialog submit events are consumed until asynchronous persistence succeeds, preserving input
and displaying validation failures inline.

Owner Member management uses a list-detail pattern. The Members list performs search and creation. Selecting a row opens
an in-page profile view with read-only payment history, while profile editing happens in the same page instead of a
separate edit dialog.

`Account` and `Role` are the implemented names for the design's `User` and `UserRole` entities. Memberships store one
purchased access period, and each has exactly one Payment. Membership and Payment creation uses one transaction.
Existing databases are upgraded by an idempotent schema-version migration that adds the required timestamps without
deleting records.

Membership display status is derived from its active flag and dates rather than stored. New active periods cannot
overlap another active period for the same Member. Deactivation does not change `accounts.is_active`, so it does not
prevent authentication. `OwnerMemberService.hasValidMembership(memberId, date)` is the shared contract for future
Member entry validation; it returns true when any active Membership covers the date inclusively.

## Build, testing, and CI

Gradle compiles against Java 25 and runs JUnit 5 and Checkstyle:

```shell
./gradlew clean check
```

`releaseJars` produces Windows x64, Linux x64, macOS x64, and macOS ARM64 executable JARs. Each JAR includes the
matching JavaFX and SQLite native libraries; the verification task checks required resources and native contents.

GitHub Actions runs checks and the matching packaging task on all four operating-system targets. CodeQL analyzes Java
on pushes, pull requests, and a weekly schedule.

## Development process

Features are developed on role-and-feature-specific branches and reviewed before merging into `master`. Behavioural
changes use a failing-test-first workflow. AI interaction summaries are stored under `logs/<member>/`, and generated
summaries remain marked pending until the named team member verifies them.

Documentation must describe the latest released behaviour precisely. Update this guide and the User Guide in the same
feature branch as any affected behaviour.

## Acknowledgements

- The initial visual direction was adapted from Google Stitch mock-ups created for GymFlow; generated HTML was used
  only as a visual reference and was not copied into the JavaFX implementation.
- OpenAI Codex assisted with planning, implementation, testing, review, and interaction-log summaries.
- The Ponytail plugin was used to review changes for unnecessary code and speculative abstractions.
- The Superpowers plugin supplied brainstorming, planning, TDD, debugging, and verification workflows.
- GymFlow uses OpenJFX and the Xerial SQLite JDBC driver. Their respective projects retain ownership of their code and
  licences.

Add every externally reused idea, code fragment, asset, or document to this section when it is introduced.
