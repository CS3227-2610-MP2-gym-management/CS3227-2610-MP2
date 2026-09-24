# GymFlow Architecture

## Purpose

This file is the implementation contract for GymFlow. Coding agents must read it before changing production code.
It describes the intended architecture for the whole product, including planned features, while distinguishing that
target from what is implemented today.

Use the project documents as follows:

1. [`docs/ProjectDecisions.md`](docs/ProjectDecisions.md) defines agreed product rules, deferred decisions, and open
   questions. Do not implement an open or KIV choice as if it were agreed.
2. [`docs/UserStories.md`](docs/UserStories.md) defines feature scope, priority, ownership, and implementation status.
3. [`docs/UserGuide.md`](docs/UserGuide.md) is the source of truth for behaviour available to current testers.
4. This file defines component boundaries and implementation rules.
5. [`docs/DeveloperGuide.md`](docs/DeveloperGuide.md) describes the current implementation in greater detail.

If documents disagree, preserve current tested behaviour and stop to resolve the product conflict. Do not silently
choose the easiest interpretation. Update the affected documents when an agreed behaviour changes.

## System context

GymFlow manages one gym with one Owner and multiple Members. It is currently a local Java SE 25 desktop application:

```text
JavaFX views
    -> application services
        -> SQLite stores
            -> data/gymflow.db
```

The completed product also includes Member authentication and screens, self-registration and approval, attendance,
membership plans, workouts, body-weight tracking, trends, broader statistics, and an administrative audit history.
The backlog determines when these are implemented.

Cloud architecture is KIV. A future shared deployment will be online-only and must support concurrent clients, but no
server framework, protocol, cloud database, or synchronization mechanism has been selected. Keep business rules out
of JavaFX and JDBC-specific code so a future network/persistence boundary can be introduced without rewriting the
domain behaviour.

## Architectural principles

- Prefer a small layered design over framework-driven or speculative abstractions.
- Put each rule in one authoritative service or domain function and reuse it from Owner and Member workflows.
- Enforce important integrity rules both in services and, where possible, with database constraints.
- Make multi-record business operations atomic.
- Derive statuses and totals from source data instead of persisting duplicate state.
- Preserve historical records; use deactivation, withdrawal, or a compensating record instead of silent deletion or
  mutation where the product decisions require history.
- Keep secrets and internal identifiers out of UI messages, exports, and logs.
- Add an interface only when there is a real second implementation, external boundary, or useful test seam.
- Implement only approved stories. Do not introduce behaviour for unresolved product decisions.

## Layers and dependency rules

### Composition root

`GymFlowApp` owns startup and dependency construction. It initializes persistence, creates services, and supplies them
to `AppView`. Avoid service locators, global mutable state, and constructing stores or databases inside views.

### Presentation layer

Package: `com.gymflow.ui`

Responsibilities:

- Render JavaFX screens, dialogs, navigation, formatting, themes, and accessible feedback.
- Hold transient form and selection state.
- Perform lightweight input restrictions for immediate feedback.
- Call services for all reads and writes.
- Run hashing, database, file, and future network work off the JavaFX Application Thread.
- Apply results and UI state changes on the JavaFX Application Thread.

Views must not issue SQL, enforce the only copy of a business rule, inspect password hashes, or bypass route/session
authorization. Preserve entered form values when an operation fails and show a safe, actionable message.

### Application service layer

Current feature packages: `com.gymflow.auth`, `com.gymflow.member`, `com.gymflow.visit`, `com.gymflow.expense`, and
`com.gymflow.announcement`.

Services define use cases and are the authoritative boundary for:

- input normalization and business validation;
- authentication and authorization;
- orchestration across records or stores;
- transaction requirements;
- translating persistence failures into stable, non-sensitive application failures.

Member-facing and Owner-facing screens should reuse shared rules rather than duplicate them. For example, entry
eligibility must use one service contract regardless of which screen initiates check-in.

Place a planned feature in a focused package when implemented, such as workouts or body metrics. Do not expand
`OwnerMemberService` into a universal application service merely because it already has database access.

### Persistence layer

Package: `com.gymflow.data`

Stores own SQL, transactions, row mapping, and persistence-level authorization checks. They return model records or
purpose-built read projections, not JDBC objects. Connections, statements, and results must use try-with-resources.

`GymFlowDatabase` owns connection creation, schema initialization, ordered migrations, and local reset. Every SQLite
connection enables foreign keys. New tables and indexes must be created centrally, migrated transactionally, covered
by tests, and included in reset behaviour.

SQL constraints are required for invariants vulnerable to races or programming errors, including uniqueness,
foreign keys, positive amounts, valid date ordering, and one open Visit per Member. Service validation is still
required to provide useful errors.

### Model layer

Package: `com.gymflow.model`

Use immutable records and enums for domain values and read projections. Models must not depend on JavaFX, JDBC, or
view controls. Fixed closed sets belong in enums; calculated display states must remain derived.

Persistence-only secrets, such as password hashes and salts, must not appear in general account models returned to
the UI.

### Monitoring and export utilities

`com.gymflow.monitoring` may record startup and sanitized failure types, never passwords, credential material, or form
contents. UI export utilities may serialize already-loaded projections when export semantics are explicitly “what is
currently displayed.” Export logic must prevent CSV formula injection and exclude secrets and internal IDs.

### Allowed dependency direction

```text
ui -> services -> data -> java.sql
 |       |          |
 +------ model <----+

monitoring -> JDK logging
```

The model layer depends only on the JDK. Data must not depend on UI. Services must not depend on JavaFX controls.
Views may depend on models and services but not stores.

## Domain model and invariants

### Account and MemberProfile

- `Account` represents identity, credentials, role, and login permission.
- `MemberProfile` contains Member-specific personal information and belongs only to a `MEMBER` account.
- Email is trimmed, normalized to lowercase, and unique without regard to case.
- Passwords are 12-128 characters in the current policy and are stored only as salted PBKDF2-HMAC-SHA256 hashes.
- Authentication returns the same public failure for an unknown email, wrong password, or disabled account.
- Plaintext password buffers are cleared after use and never logged.
- One installation has exactly one Owner.

Account state and Membership state are independent. Account deactivation is an administrative/security action;
Membership expiry must not disable an established account.

Self-registration is planned. The target flow creates a pending account, then the Owner confirms in-person payment
and activates the account while creating its first Membership and Payment atomically. The exact `PENDING`, `ACTIVE`,
and `DEACTIVATED` representation remains open. Do not implement self-registration until that status model and its
acceptance criteria are agreed.

### MembershipPlan, Membership, and Payment

- `MembershipPlan` is planned and defines a reusable name, duration, current price, and sale availability.
- `Membership` is one purchased access period for one Member.
- `Payment` is the Owner-recorded in-person payment for that purchase; GymFlow does not process payments online.
- Current scope has exactly one positive Payment per Membership and fixed SGD currency.
- Store money as integer cents in persistence; use `BigDecimal` at input/calculation boundaries where appropriate.
- Every purchase or renewal creates a new Membership and Payment. Do not extend or overwrite an old purchase.
- Active Membership periods for one Member must not overlap.
- A purchase must snapshot the plan and price values needed for historical accuracy.
- Plan edits must not rewrite past purchases.
- Membership deactivation preserves history and does not imply a refund.
- Refunds, instalments, voids, and payment corrections are open decisions.

Membership display status is derived as `ACTIVE`, `UPCOMING`, `EXPIRED`, or `DEACTIVATED`. It is not persisted.

### Gym-entry eligibility and Visit

One shared service operation must decide entry eligibility. A Member may check in only when:

```text
account is active
AND an active Membership exists
AND membership.startDate <= gymToday
AND gymToday <= membership.expiryDate
AND no open Visit exists for the Member
```

Visit rules:

- A Visit has a required entry `Instant` and optional exit `Instant`.
- A null exit means the Visit is open; do not store a separate check-in status.
- A Member may have at most one open Visit.
- Check-out requires an open Visit and remains allowed after Membership expiry or deactivation.
- Exit cannot precede entry.
- Owner correction requires a reason and records the latest correction time and Owner.
- Deactivating access never invents an exit or closes an existing Visit.

### Expense

- Expenses are immutable operating records in the current scope.
- Amounts are positive SGD values with at most two decimal places.
- Expense dates cannot be in the future.
- Editing, deletion, and export remain outside current implemented scope unless a story is approved.

### Announcement

- Title and content are required.
- Withdrawal sets `withdrawnAt`; it does not delete or edit history.
- Members see only published announcements.
- Read/unread tracking is not required by current stories.

### Workout, WorkoutSet, and BodyMetric

These P1 entities are planned:

- `Workout` belongs to a Member and records when a session occurred plus optional notes.
- `WorkoutSet` belongs to a Workout and records exercise name, ordered set number, non-negative repetitions, and
  non-negative weight.
- `BodyMetric` records a positive body weight for a Member on a date.

Keep the initial model specific to the stories. Do not add exercise catalogues, generic metric frameworks, body-fat
tracking, or other speculative entities. Trends are read projections calculated from stored workouts and body weights.

### AuditLog

An append-only administrative audit history is planned. Do not treat the Visit's latest correction fields as a full
audit log. The exact audited actions, before/after representation, and retention policy remain open and must be agreed
before implementation.

## Time, dates, and ordering

- Store event timestamps as UTC ISO-8601 `Instant` values.
- Display instants in the appropriate local zone; the authoritative gym timezone for a shared deployment is open.
- Use `LocalDate` for Membership periods, expense dates, dates of birth, and body-weight measurement dates.
- Membership expiry is inclusive.
- Prefer an injected `Clock` for new time-dependent service logic and tests. Avoid calling `now()` throughout UI,
  service, and store code independently.
- Queries must define deterministic ordering, including a stable tie-breaker when timestamps can be equal.

## Transactions and consistency

A service use case defines the atomic boundary; a store may implement it with one JDBC transaction. At minimum, these
operations must be all-or-nothing:

- current Owner-created Member onboarding: Account, MemberProfile, Membership, and Payment;
- planned self-registration approval: account activation, Membership, and Payment;
- renewal/purchase: Membership and Payment;
- any correction that changes multiple related records;
- schema migration and full local reset.

Rollback on every failure. Never expose a partially completed aggregate. Before cloud work begins, concurrency,
optimistic locking, and duplicate-request/idempotency behaviour must be agreed rather than guessed.

## Validation and error handling

Apply validation at three levels:

1. JavaFX restrictions provide immediate feedback but are not authoritative.
2. Services normalize and validate every externally supplied value.
3. Database constraints protect persistent integrity and concurrent paths.

Centralize reusable validators when a rule is shared across use cases. Do not let Owner and Member flows develop
different email, phone, password, money, or date policies accidentally.

Expected validation and conflict failures should produce stable, safe messages. Unexpected exceptions are logged only
in sanitized form and presented without SQL, paths, stack traces, credentials, or form contents.

## Authorization

- The current in-memory session controls navigation, but privileged service/store operations must independently
  verify an active Owner where they perform writes.
- Owner routes require an authenticated `OWNER`; Member routes require an authenticated `MEMBER` whose account may
  log in.
- Every Member read or write must be scoped to the authenticated Member unless the Owner use case explicitly grants
  broader access.
- A Member must never select an arbitrary member ID to access another Member's profile, visits, workouts, metrics, or
  payments.
- Lack of a valid Membership denies entry, not ordinary access to an already activated account.

## Feature implementation pattern

For a new use case:

1. Confirm an approved story and resolve any relevant open decision.
2. Define or extend immutable model records and request objects.
3. Add the service operation with validation, authorization, and an explicit transaction boundary.
4. Add the smallest required store queries and database constraints.
5. Add an ordered, idempotent schema migration when persistence changes.
6. Add the JavaFX screen or interaction using services only, with blocking work off the UI thread.
7. Test service behaviour, persistence constraints, rollback, authorization, and relevant UI helpers.
8. Update User Stories, User Guide, Developer Guide, Project Decisions, and this file when their facts change.

Do not begin with a table or screen and infer the domain rules afterward.

## Testing and verification

- Use JUnit 6 and isolated temporary SQLite databases for persistence tests.
- Test observable behaviour rather than private implementation details.
- Every business rule needs service-level coverage; every database constraint or migration needs integration coverage.
- Multi-record operations need rollback tests.
- Authorization tests must cover both allowed and denied roles and cross-Member access.
- Time-dependent tests should use fixed clocks/dates.
- CSV tests must cover quoting, Unicode, newlines, and formula-injection protection.
- JavaFX layout, focus, scrolling, dialogs, accessibility, theme contrast, and native launch require manual checks where
  automated coverage is impractical.
- Run `gradlew.bat check` on Windows or `./gradlew check` elsewhere before handoff. Run the relevant release task when
  packaging, module declarations, resources, or dependencies change.

## Cloud-readiness constraints

Until the cloud decision is approved:

- Do not add networking, remote credentials, background synchronization, or a second database implementation.
- Do not expose SQLite/JDBC types beyond the data layer.
- Do not encode local filesystem assumptions into domain services.
- Do not rely on a client clock for a future security-sensitive access decision.
- Do not assume writes cannot race; retain database constraints even if the local UI makes a race unlikely.
- Do not ship database or server secrets in a desktop JAR.

When shared deployment is approved, preserve the service use-case semantics and introduce the remote boundary around
them. Online-only means failure is surfaced clearly; it does not imply offline write queues or later synchronization.

## Prohibited shortcuts

- SQL or store construction in JavaFX views.
- Business rules implemented only by disabling a button.
- Persisted fields for values that can be reliably derived, such as Membership status or Visit open/closed status.
- Floating-point types for money.
- Plaintext passwords, password logging, or secrets in general models.
- Editing old Membership or Payment records to represent a new purchase.
- Closing Visits as a side effect of Membership deactivation.
- Hard deletion where the agreed model requires historical retention.
- Silent schema changes without a versioned migration and migration test.
- New frameworks, repositories, factories, or generic abstractions without a demonstrated need.
- Implementing an open or KIV decision without human agreement.

## Current implementation map

| Area | Current state | Intended extension |
| --- | --- | --- |
| Owner authentication | Implemented | Preserve one-Owner rule |
| Member accounts/profiles | Owner creation and editing implemented | Add agreed self-registration/approval and Member login |
| Memberships/Payments | Explicit periods and amounts implemented | Add MembershipPlan and purchase snapshots |
| Member attendance | Shared schema and Owner oversight implemented | Add Member check-in, check-out, state, and history screens |
| Announcements | Owner management and published query implemented | Display published notices to Members |
| Expenses/finances | Owner creation, filtering, and totals implemented | Extend only through approved stories |
| Workouts/body weight | Not implemented | Add focused models, services, stores, views, and derived trends |
| Statistics | Current counts and financial totals implemented | Add attendance and peak-use projections |
| Audit history | Not implemented | Add only after audit scope and retention are agreed |
| Shared cloud deployment | KIV | Preserve boundaries; do not choose infrastructure yet |

## Handoff checklist for coding agents

Before completing a change, verify:

- The behaviour comes from an approved story or explicit request.
- No open/KIV decision was silently resolved.
- Dependency direction and package ownership remain correct.
- Shared rules are reused rather than duplicated.
- Authorization and transaction boundaries are explicit.
- Persistence constraints and migrations preserve existing data.
- No sensitive data enters logs, errors, exports, or UI models.
- Blocking work stays off the JavaFX Application Thread.
- Relevant automated tests and Checkstyle pass.
- Current behaviour and decision documentation remain accurate.
