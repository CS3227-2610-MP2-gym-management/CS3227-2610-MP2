# GymFlow Developer Guide

## Architecture

GymFlow is a modular Java SE 25 and JavaFX 25 desktop application. It uses a single local SQLite database and one
JavaFX `Scene`; `AppView` replaces the centre of a persistent application shell when navigating so screen changes do
not create extra windows. The shell owns the global theme control and applies one `dark` style class to every screen.

The current code is divided by responsibility:

- `com.gymflow.ui`: application startup, navigation, and JavaFX views.
- `com.gymflow.auth`: password hashing and authentication rules.
- `com.gymflow.data`: SQLite initialization and account persistence.
- `com.gymflow.expense`: Owner-side operating-expense validation and queries.
- `com.gymflow.model`: shared account and role data.
- `com.gymflow.member`: Owner-side Member onboarding and profile rules.
- `com.gymflow.monitoring`: local startup and unexpected-error diagnostics.
- `com.gymflow.visit`: read-only Owner attendance queries.

Concrete classes are used instead of repository interfaces or factories because each responsibility currently has one
implementation. New abstractions should be introduced only when a second implementation or a real testing boundary
requires one.

The selected theme is stored with Java `Preferences`, independently of the SQLite application data. Dialogs use their
own JavaFX scenes, so `UiComponents.styleDialog` copies the active `dark` class from the owning application shell.
Theme colours remain centralized in `app.css`; individual views do not contain theme-specific styling.

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

Owner Member management uses a list-detail pattern. Virtualized JavaFX `ListView` card lists display every record type,
wrap long values, and retain vertical scrolling at the minimum window size. The Members list performs search and
creation. Selecting a card opens
an in-page profile view with read-only payment history, while profile editing happens in the same page instead of a
separate edit dialog.

Member password replacement follows the same security boundary as onboarding. `OwnerMemberService` validates the new
password, creates a fresh PBKDF2 hash and salt, and clears the caller's character array on every outcome.
`OwnerMemberStore` updates only the credential columns and account update timestamp when the requester is an active
Owner and the target is a Member. The Owner's password is not requested again because the Owner session and store-level
role check already authorize the operation; full GymFlow reset retains its stronger reauthentication guard because it
deletes every record.

The Owner sidebar exposes the full reset from every Owner screen through one shared dialog. The dialog still requires
the current Owner password and exact `RESET` confirmation; moving the entry point does not weaken authorization. The
Member sidebar does not receive this action.

`Account` and `Role` are the implemented names for the design's `User` and `UserRole` entities. Memberships store one
purchased access period, and each has exactly one Payment. Membership and Payment creation uses one transaction.
Existing databases are upgraded by an idempotent schema-version migration that adds the required timestamps without
deleting records.

Membership display status is derived from its active flag and dates rather than stored. New active periods cannot
overlap another active period for the same Member. Deactivation does not change `accounts.is_active`, so it does not
prevent authentication. `OwnerMemberService.hasValidMembership(memberId, date)` is the shared contract for future
Member entry validation; it returns true when any active Membership covers the date inclusively.

`PaymentOverview` joins an immutable `MemberPayment` to the Member identity and purchased Membership period required by
the Owner Finances page's Income tab. `OwnerMemberStore.searchPayments` matches Member name or email only and orders records by
payment time and ID descending. The global page is deliberately read-only; Member onboarding and Membership renewal
remain the only Payment creation paths.

`OwnerMemberStore.ownerDashboard` supplies the Owner overview without another service layer. It counts registered
Member profiles and distinct Members with an active Membership covering today, and totals every recorded Payment.
It also returns the five most recently created Members. Each overview card selects the currently valid active
Membership first, otherwise the nearest upcoming active Membership, otherwise the latest historical Membership.
`OwnerVisitService.currentVisitorCount` remains the source of the separate open-Visit count.

Operating Expenses are stored separately because they have no Member or Membership relationship. `OwnerExpenseService`
validates immutable additions and exposes all/category listings plus the all-recorded-time total.
`OwnerExpenseStore` stores amounts as integer SGD cents and verifies the recording account is an active Owner. Expense
editing and deletion are intentionally not implemented. The Owner overview subtracts the Expense total from membership
income to derive the all-recorded-time net value.

The shared `Visit` record maps to the `visits` table. `Account`, `Role`, and `member_account_id` are the implemented
names for the design's `User`, `UserRole`, and `Visit.memberId` concepts. A null `exited_at` derives the currently
checked-in state. A partial unique index prevents more than one open Visit per Member, and a table constraint prevents
an exit from preceding entry. Visit timestamps use fixed UTC ISO-8601 millisecond form
`yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`; this makes the database constraint and ordering exact at the supported precision.
`OwnerVisitService` exposes all/current searches, per-Member history, the current visitor count, and Owner correction.
Schema version 3 adds nullable latest-correction metadata and transactionally rebuilds version-2 Visit tables while
preserving their rows. A correction atomically replaces the timestamps and latest reason after confirming an active
Owner, valid timestamp order, and a real change. The existing unique index also prevents a correction from reopening a
Visit when that Member already has another open Visit. Member-side entry and exit orchestration is intentionally left
to the Member feature owner, who can use the existing Membership-validity contract before inserting a Visit.

Deactivating a Membership deliberately does not close an existing open Visit. The Member remains currently checked
in until the Member-side exit workflow supplies the real exit time; otherwise deactivation would fabricate attendance
data. Deactivation makes `hasValidMembership(memberId, date)` return false for subsequent entry attempts while leaving
the Member account and existing Visit history unchanged.

Announcements use a separate concrete store and service because they are gym-wide records rather than Member-owned
data. Schema version 5 adds the `announcements` table. Publication and withdrawal verify an active Owner in the write
query. Withdrawal sets `withdrawn_at` and `updated_at` instead of deleting the record. `listPublished()` is the shared
read-only contract for the future Member interface; read/unread tracking and Member UI remain outside this branch.

## Build, testing, and CI

Gradle compiles against Java 25 and runs JUnit 5 and Checkstyle:

```shell
./gradlew clean check
```

`releaseJars` produces Windows x64, Linux x64, macOS x64, and macOS ARM64 executable JARs. Each JAR includes the
matching JavaFX and SQLite native libraries; the verification task checks required resources and native contents.

GitHub Actions runs checks and the matching packaging task on all four operating-system targets. CodeQL analyzes Java
on pushes, pull requests, and a weekly schedule. A separate workflow deploys the dependency-free `site/` directory
to GitHub Pages after changes reach `master`, while a scheduled workflow requests the live URL every six hours and
fails visibly when it cannot obtain a successful response.

`AppMonitoring` uses the JDK logging API rather than another dependency. Application startup and sanitized uncaught
exception types are written to three rotating files, each limited to approximately 1 MB, under `data/logs/`. Exception
messages are deliberately excluded because they could contain values entered into a form. Runtime `.log` files are
ignored by Git under `data/logs/`, while the separate top-level `logs/` directory contains only reviewed Markdown AI
interaction summaries required by the assignment.

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
