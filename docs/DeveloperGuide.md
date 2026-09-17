# GymFlow Developer Guide

## Product and technology

GymFlow is a local-first Java SE 25 desktop application for a small gym. The current release provides Owner account
setup and login, Member administration, Membership and Payment records, Expenses, Visit oversight and correction,
Announcements, application reset, and persistent light/dark themes. Member authentication and operational Member
workflows are still under development; `MemberHomeView` remains a static preview.

The application uses JavaFX 25 for its interface, SQLite through Xerial JDBC for persistence, Gradle for builds and
packaging, JUnit 6 for automated tests, and Checkstyle for source checks. It does not require a server or network
connection during normal use.

## Architecture

GymFlow follows a small layered design with one composition root and concrete implementations:

```text
GymFlowApp
    |
    v
AppView and JavaFX views              navigation, session guard, presentation
    |
    v
Authentication and Owner services     validation, authorization, use-case rules
    |
    v
SQLite stores                         queries, transactions, row mapping
    |
    v
GymFlowDatabase -> data/gymflow.db    schema, migrations, connections, reset

Shared model records and enums are used across the service, persistence, and UI layers.
```

The UI does not issue SQL. Views call services, services validate inputs and delegate to concrete stores, and stores
own persistence operations. Repository interfaces, factories, and dependency-injection frameworks are intentionally
omitted because each responsibility currently has one implementation.

### Package responsibilities

| Package | Responsibility |
| --- | --- |
| `com.gymflow.ui` | Application startup, navigation, JavaFX screens, dialogs, formatting, and themes |
| `com.gymflow.auth` | Password hashing, Owner setup, authentication, and reset authorization |
| `com.gymflow.member` | Owner-side Member, Membership, Payment, and dashboard rules |
| `com.gymflow.expense` | Owner-side Expense validation and queries |
| `com.gymflow.visit` | Owner-side Visit searches, counts, history, and corrections |
| `com.gymflow.announcement` | Announcement publication, listing, and withdrawal |
| `com.gymflow.data` | SQLite schema management, stores, transactions, and row mapping |
| `com.gymflow.model` | Shared immutable records and fixed-value enums |
| `com.gymflow.monitoring` | Local startup and sanitized unexpected-error diagnostics |

### Application shell and navigation

`GymFlowApp` initializes the database and services before constructing `AppView`. `AppView` owns one JavaFX `Scene`
and replaces the centre of a persistent shell when navigating, so screen changes do not create extra windows. Every
Owner route checks that the in-memory session exists and has the `OWNER` role. Store-level active-Owner checks repeat
authorization for persistent write operations rather than trusting the UI alone.

The shell also owns the theme control. Java `Preferences` stores the selected theme independently of gym data, and
`UiComponents.styleDialog` applies the current theme to dialogs because each JavaFX dialog has its own scene.

## Main components

### Authentication and accounts

An installation supports one Owner account. On first launch, the Login screen enters setup mode when no Owner exists.
Emails are trimmed, lowercased, and stored under a case-insensitive uniqueness constraint.

Passwords use PBKDF2-HMAC-SHA256 with 600,000 iterations, a random 16-byte salt, and a 32-byte derived hash. Only the
Base64-encoded hash and salt are stored. Verification uses a constant-time comparison, and services clear submitted
password character arrays on every outcome. Hashing and database operations run outside the JavaFX Application
Thread.

The full reset requires the current Owner password and exact `RESET` confirmation. `GymFlowDatabase` drops and
recreates all application tables inside one transaction, allowing SQLite to roll back a failed reset.

### Members, Memberships, and Payments

Member onboarding creates an account, generated Member number, profile, initial Membership, and Payment in one
transaction. Profile validation is authoritative in `OwnerMemberService`:

- Email must contain exactly one `@` with text on both sides and is normalized to lowercase.
- Phone numbers are eight-digit Singapore numbers beginning with `3`, `6`, `8`, or `9` and are stored as
  `+65 XXXX XXXX`.
- Date of birth is optional; when supplied, the Member must be at least 12 years old.
- Passwords contain 12–128 characters.
- Monetary amounts are positive, limited to two decimal places, and stored as integer SGD cents.

Member search escapes SQL wildcard characters and matches names or emails without regard to case. Selecting a Member
card opens an in-page profile containing Membership, Visit, and Payment history. Password reset replaces only the
credential fields with a freshly salted hash; it does not alter the Member's records or active state.

Each Membership represents one purchased access period and has exactly one immutable Payment. Access on a date is
derived rather than stored:

```text
membership.active
AND membership.startDate <= date
AND date <= membership.expiryDate
```

Active periods for the same Member cannot overlap. Renewal creates a new Membership and Payment. Deactivation never
disables the Member account and does not close an existing Visit. Display values such as `ACTIVE`, `UPCOMING`,
`EXPIRED`, and `DEACTIVATED` are calculated from the flag and dates.

### Visits

A Visit stores an entry time and an optional exit time. A partial unique SQLite index prevents more than one open
Visit per Member, while a table constraint prevents an exit from preceding entry. A null exit derives the currently
checked-in state; no separate Visit status is stored.

Visit timestamps are stored as UTC ISO-8601 instants and displayed in the computer's local time zone. Owners can
correct entry and exit times with a required reason. GymFlow stores the latest correction time, Owner, and reason,
rather than maintaining a separate audit-history subsystem. Reopening a Visit is rejected when that Member already
has another open Visit.

`OwnerMemberService.hasValidMembership(memberId, date)` is the shared eligibility contract for the future Member
entry workflow. Owner correction intentionally does not revalidate historical Membership eligibility.

### Finances and dashboard

Payments are membership income and can be created only with Member onboarding or a new Membership. The Finances page
joins each Payment to its Member and Membership period and otherwise keeps it read-only.

Expenses are independent immutable operating records. They contain a date, amount, method, category, optional
description, recording Owner, and creation time. Future dates, non-positive values, and amounts with more than two
decimal places are rejected. Expense editing and deletion are intentionally outside the current scope.

The Owner dashboard derives total Members, currently valid Memberships, open Visits, total income, total Expenses,
net income, and the five newest Members from the same database records shown elsewhere in the application.

### CSV export

`CsvExporter` is a package-private UI utility because exports operate on the projections already loaded by each
screen; they do not require another service or persistence query. Members, Income Payments, and Visits snapshot the
currently displayed `ListView` items before starting a background write. Consequently, the exported rows reflect the
active search and, for Visits, the selected tab even if the interface changes while the file is being written.

Files use UTF-8, CRLF records, ISO dates, and UTC ISO-8601 timestamps. Standard CSV quoting protects commas, quotation
marks, and line breaks. Text beginning with `=`, `+`, `-`, or `@` receives a leading apostrophe to prevent spreadsheet
formula interpretation. Optional values become empty cells; credentials and internal numeric IDs are never exported.
The implementation uses the JDK writer and JavaFX `FileChooser`, avoiding a CSV dependency for three fixed schemas.

### Announcements

Announcements are published by an active Owner. Withdrawal sets `withdrawn_at` and `updated_at` instead of deleting
the row, preserving Owner-visible history. `OwnerAnnouncementService.listPublished()` is the read-only contract for
the future Member interface. Read/unread tracking is not required by the current stories.

## Domain model

The implemented model follows the agreed entities while using concise Java names. `Account` corresponds to the
planning document's `User`, `Role` corresponds to `UserRole`, and `Member` combines the account identity with its
one-to-one `MemberProfile` for Owner-facing reads.

| Entity | Principal fields and relationships | Important rules |
| --- | --- | --- |
| `Account` | `id`, normalized `email`, `role`, `active`, `createdAt`, `updatedAt`; secret hash, salt, and iteration fields remain in persistence | Role is `OWNER` or `MEMBER`; email is case-insensitively unique; one Owner per installation |
| `Member` / `MemberProfile` | Account ID, unique Member number, full name, phone number, optional date of birth | Exists only for a Member account; Singapore phone and minimum-age validation apply |
| `Membership` | ID, Member ID, start date, expiry date, active flag, creation and update times | Expiry cannot precede start; active periods cannot overlap; status is derived |
| `MemberPayment` | ID, Membership ID, amount, method, paid time, optional reference, recording Owner, creation time | Exactly one immutable Payment per Membership; positive SGD amount with at most two decimals |
| `Visit` | ID, Member ID, entry, optional exit, creation time, optional latest correction metadata | At most one open Visit per Member; exit cannot precede entry; all correction fields are present together |
| `Expense` | ID, date, amount, method, category, optional description, recording Owner, creation time | Immutable, positive SGD amount; date cannot be in the future |
| `Announcement` | ID, title, content, publication time, creating Owner, optional withdrawal time, creation and update times | Required title and content; withdrawal preserves history instead of deleting the record |

`Role`, `PaymentMethod`, `ExpenseCategory`, and derived `MembershipStatus` are enums because each has a fixed set of
values. Planned entities such as `MembershipPlan`, `Workout`, `WorkoutSet`, `BodyMetric`, and `AuditLog` are not part of
the current schema and must not be treated as implemented features. The complete prioritized backlog and implementation
status are recorded in the [User Stories](UserStories.md).

## Persistence and schema evolution

`GymFlowDatabase` creates parent directories, opens SQLite connections with foreign keys enabled, initializes the
schema, applies versioned migrations, and performs full reset. The current schema version is 5.

`member_account_id` is the database foreign key corresponding to the shared model's `memberId`.

| Table | Main relationship or constraint |
| --- | --- |
| `accounts` | Normalized unique email, fixed `OWNER`/`MEMBER` role, one-Owner partial index |
| `member_profiles` | One-to-one primary/foreign key to a Member account |
| `memberships` | Many access periods belonging to one Member |
| `payments` | Exactly one Payment per Membership, recorded by an Owner |
| `visits` | Member attendance with at most one open Visit |
| `expenses` | Independent immutable operating costs recorded by an Owner |
| `announcements` | Gym-wide notices with nullable withdrawal metadata |

Migrations are ordered and idempotent through SQLite `PRAGMA user_version`. They preserve existing rows and update the
version only after successful work. The Visit migration rebuilds its table transactionally when adding constraints
that SQLite cannot apply with a simple `ALTER TABLE`. New tables automatically participate in reset because schema
creation remains centralized.

## Key design decisions

| Decision | Reason and accepted trade-off |
| --- | --- |
| One local Owner per installation | Fits one gym and makes first-run setup simple; multi-Owner administration is unsupported |
| Local SQLite database | Keeps the desktop app self-contained; installations do not share records automatically |
| Concrete services and stores | Avoids speculative interfaces; add an abstraction only when a second implementation exists |
| Immutable purchase records | Membership and Payment history remains explainable; corrections require deactivation and replacement |
| Derived statuses and totals | Prevents stored values drifting from dates and source records; values are recomputed on read |
| Integer cents for money | Avoids floating-point rounding errors; currency is fixed to SGD |
| Latest Visit correction metadata | Supports accountability with little schema cost; full correction history is not retained |
| Platform-specific release JARs | Bundles only matching JavaFX and SQLite natives; four artifacts must be produced |

## Error handling, security, and monitoring

JavaFX forms provide early restrictions, while services repeat validation so invalid values cannot bypass the UI.
Dialogs consume submit events until background work succeeds, preserving entered values and showing non-sensitive
inline failures. Multi-record writes use transactions so partial Member, Membership, or Payment records are not left
behind.

Authentication deliberately returns the same failure for unknown email, incorrect password, and inactive account.
Plain-text passwords are never stored or logged. Owner pages use a role-aware session guard, and stores independently
check active-Owner authorization before writes.

`AppMonitoring` uses the JDK logging API and adds no dependency. It writes startup events and sanitized uncaught-error
types to three rotating files under `data/logs/`, each limited to approximately 1 MB. Exception messages are excluded
because they could contain form values. Runtime `.log` files and `data/gymflow.db` are ignored by Git; the separate
top-level `logs/` directory contains reviewed AI interaction summaries.

## Build, testing, CI, and deployment

Useful commands from the repository root are:

```shell
./gradlew run          # compile and launch on the current platform
./gradlew test         # run JUnit 6 tests
./gradlew check        # run tests and Checkstyle
./gradlew releaseJars  # build and verify all four platform JARs
```

Windows uses the equivalent commands through `gradlew.bat`. Release tasks produce self-contained JARs for Windows
x64, Linux x64, macOS x64, and macOS ARM64. `Launcher` provides a plain Java entry point so packaged JARs can reach
the bundled JavaFX runtime. `verifyReleaseJars` checks the stylesheet, SQLite service metadata, and matching native
libraries.

Automated test responsibilities are grouped as follows:

| Area | Observable behavior covered |
| --- | --- |
| Authentication | Setup, email normalization, credential failures, password hashing, reset authorization |
| Persistence | Schema creation, migrations, database constraints, transactions, and full reset |
| Members and Memberships | Validation, atomic onboarding, search, renewal, overlap, activation, Payments, dashboard |
| Visits | Search, current visitors, history, ordering, correction rules, and open-Visit uniqueness |
| Expenses and Announcements | Authorization, validation, ordering, totals, filtering, publishing, and withdrawal |
| UI helpers | Theme behavior, resources, card components, financial input, Visit formatting, Owner route guard, CSV encoding |
| Monitoring and packaging | Sanitized rotating logs and required release-JAR contents |

JavaFX layout, keyboard focus, dialogs, scrolling, theme contrast, and native launch remain manual-test concerns.
Release verification should cover first-run setup, login, each Owner page, invalid input retention, reset cancellation,
database persistence after restart, and the matching JAR on each supported platform.

The **Tests** GitHub Actions workflow runs `check` and the matching release task across Windows, Linux, Intel macOS,
and Apple silicon macOS on pushes and pull requests. **CodeQL** analyzes Java on the same events and weekly. The
**Pages** workflow deploys the dependency-free `site/` directory from `master`, and **Website Uptime** checks the live
site every six hours.

## Software engineering process

Work is divided by user role and developed on descriptive feature branches. Changes are reviewed before reaching
`master`, and behavioral changes use a failing-test-first workflow. A feature is complete when its reachable behavior
matches the approved stories, relevant automated tests and Checkstyle pass, platform packaging still succeeds, manual
JavaFX checks are complete, and affected documentation is current.

OpenAI Codex assists with requirements refinement, planning, implementation, testing, debugging, review, and
documentation. Superpowers supplies structured brainstorming, planning, TDD, debugging, and verification workflows;
Ponytail reviews changes for unnecessary code and speculative abstractions. Humans retain responsibility for scope,
design approval, manual acceptance, generated-summary verification, commits, and merges. Interaction summaries live
under `logs/<member>/` and remain marked pending until the named member reviews them.

## Extension points

- Implement Member authentication by routing an authenticated `MEMBER` session to Member screens while retaining the
  centralized Owner-role guard.
- Validate Member entry with `hasValidMembership(memberId, date)` before inserting a Visit, and require an open Visit
  before exit.
- Read active notices through `OwnerAnnouncementService.listPublished()` for the future Member dashboard.
- Keep new schema changes ordered, versioned, transactional, and included in centralized reset.
- Add new persistence abstractions only when another implementation or a genuine test boundary requires them.

## Acknowledgements

- The initial visual direction was adapted from Google Stitch mock-ups created for GymFlow. Generated HTML was used
  only as a visual reference and was not copied into the JavaFX implementation.
- [OpenAI Codex](https://openai.com/codex/) assisted with planning, implementation, testing, review, and interaction-log
  summaries. All generated output was reviewed and adapted for this project.
- The Ponytail plugin guided simplification reviews; no Ponytail source code is included in GymFlow.
- The Superpowers plugin supplied development-process skills; no Superpowers source code is included in GymFlow.
- GymFlow uses [OpenJFX](https://openjfx.io/), the
  [Xerial SQLite JDBC driver](https://github.com/xerial/sqlite-jdbc),
  [Gradle](https://gradle.org/), and [JUnit](https://junit.org/). Their projects retain ownership of their code and
  licences.
- Repository automation uses [GitHub Actions](https://github.com/features/actions),
  [CodeQL](https://codeql.github.com/), and
  [GitHub Pages](https://docs.github.com/en/pages).

Add every externally reused idea, code fragment, asset, or document to this section when it is introduced.
