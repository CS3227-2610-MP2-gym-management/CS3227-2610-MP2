# Local and Live Cloud Deployment Plan

## Outcome

GymFlow will support two isolated environments that use the same backend architecture:

- A local development environment for repeatable development, automated tests, and fake data.
- A hosted live environment for production-readiness testing and eventual real use.

Both environments will use PostgreSQL, Supabase Auth, the Supabase Data API, Row Level Security (RLS), and the same
version-controlled database migrations. The live desktop application will allow both Owners and Members to sign in
with credentials created through a controlled process. A newly downloaded copy of GymFlow will no longer assume that
it must create a new Owner.

SQLite will remain available temporarily as the source for legacy-data migration and existing regression tests. It
will not be maintained as a permanent alternative production backend.

## Target architecture

```text
Local development                         Live production
JavaFX application                        Release JavaFX application
        |                                           |
        v                                           v HTTPS
Local Supabase stack                      Hosted Supabase project (Singapore)
  - PostgreSQL                              - PostgreSQL
  - Auth                                    - Auth
  - Data API                                - Data API
  - Edge Functions                          - Edge Functions
  - Mailpit                                 - Production email provider
```

The two environments share:

- Schema migrations and database functions.
- RLS policies and database tests.
- Edge Function source.
- Java authentication and data-access implementations.
- Owner and Member authorization rules.

They differ only in endpoint configuration, publishable keys, data, email delivery, and operational safeguards.

## Environment configuration

Use an explicit environment selector and endpoint configuration:

```text
GYMFLOW_ENV=local|production
GYMFLOW_SUPABASE_URL=<environment URL>
GYMFLOW_SUPABASE_PUBLISHABLE_KEY=<environment publishable key>
```

Configuration rules:

- Development runs default to `local`.
- Release builds default to `production`.
- Local builds display a prominent `LOCAL DEVELOPMENT` indicator.
- Production must never silently fall back to a local endpoint or SQLite.
- A local reset must refuse any endpoint other than `localhost` or `127.0.0.1`.
- Database passwords, secret keys, and service-role credentials must never be stored in Java code, resources, Git, or
  release JARs.
- A project URL and publishable key may be shipped to clients because RLS, grants, and the authenticated user's token
  are the authorization boundary.
- Startup diagnostics may show the selected environment and endpoint, but must never print tokens.

## Phase 0 - Preserve the existing system

### Goal

Create a safe baseline before changing persistence or authentication.

### Steps

1. Copy `data/gymflow.db` to a backup outside the repository.
2. Record source row counts for all application tables.
3. Record financial totals and several representative Member histories for later comparison.
4. Run the existing automated tests and Checkstyle checks.
5. Keep a runnable copy of the current SQLite release until live acceptance testing is complete.

### Gate

- The SQLite backup opens successfully.
- Counts and reference totals have been recorded.
- The unchanged application and tests still run.

## Phase 1 - Establish local Supabase

### Goal

Provide a reproducible local backend with production-equivalent database, authentication, API, and authorization
behaviour.

### Steps

1. Install a Docker-compatible runtime and the Supabase CLI.
2. Initialize the repository-scoped `supabase/` directory.
3. Add directories for migrations, Edge Functions, seed data, and database tests.
4. Start the local Supabase stack.
5. Add clearly fake local identities, such as an Owner, Member A, and Member B.
6. Use Mailpit for all local authentication-email tests.
7. Document commands for starting, rebuilding, and stopping the local environment.

### Tests and gate

- Local Studio, PostgreSQL, Auth, Data API, and Mailpit are reachable.
- Rebuilding from an empty local database succeeds using committed files only.
- Seeded users can authenticate.
- Stopping the backend makes the local API health check fail as expected.
- Restarting it restores the local services and seeded logins without manual data repair.

The JavaFX connection-error experience is verified in Phase 3 after the application uses Supabase Auth.

## Phase 2 - Translate and version the schema

### Goal

Make PostgreSQL migrations the authoritative schema for both local and live environments.

### Steps

1. Replace SQLite auto-increment definitions with PostgreSQL identity or UUID keys as appropriate.
2. Link application profiles to `auth.users.id`.
3. Remove application-owned password hashes, salts, and iteration fields.
4. Convert integer flags, timestamp strings, and date strings to native PostgreSQL types.
5. Preserve foreign keys, checks, uniqueness constraints, deletion behaviour, and partial indexes.
6. Move multi-record operations that must be atomic into PostgreSQL functions or protected Edge Functions.
7. Store every change as a forward migration in source control.

### Tests and gate

- A fresh local reset recreates the complete schema.
- Invalid foreign keys, dates, amounts, and overlapping records are rejected.
- Account identity uniqueness and the one-open-workout rule remain enforced. The temporary single-Owner rule is
  replaced by the active-Owner invariants in Phase 7A.
- Database lint and automated database tests pass.

## Phase 3 - Replace first-launch setup with universal sign-in

### Goal

Allow an Owner or Member to sign in from any installation without making each installation create a local Owner.

### Steps

1. Always show the sign-in screen at startup.
2. Authenticate email and password through Supabase Auth.
3. Load the authenticated user's active profile and role.
4. Route Owners and Members to their respective dashboards.
5. Refresh sessions safely and clear user state completely on sign-out.
6. Disable public signup.
7. Seed or offer a development-only local Owner setup path, but never include public Owner creation in production.
8. Create the first live Owner through a controlled deployment procedure.

### Tests and gate

- Owner and Member login work on a clean installation.
- Incorrect credentials produce a neutral error.
- Inactive accounts cannot access the application.
- Expired sessions refresh or return safely to sign-in.
- Offline and service-unavailable errors are distinguishable from invalid credentials.
- No production client can claim the Owner role.

## Phase 4 - Implement and verify authorization

### Goal

Enforce access at the database boundary even if a user modifies client requests.

### Steps

1. Enable RLS on every exposed table.
2. Revoke unnecessary `anon` and `authenticated` grants.
3. Add a policy for each permitted select, insert, update, and delete operation.
4. Allow Members to access only their own permitted profile, membership, visit, workout, and metric records.
5. Prevent Members from viewing expenses, other Members, all payments, role fields, and activation controls.
6. Allow Owners to perform the intended administrative operations.
7. Move privileged account creation and role changes into authenticated server-side functions.

### Tests and gate

Run every authorization test as an Owner, Member A, Member B, and a signed-out caller:

- Member A can access permitted Member A data.
- Member A cannot access Member B data by substituting an identifier.
- Members cannot read or change Owner-only data.
- Members cannot promote or reactivate themselves.
- Signed-out requests expose no gym data.
- The publishable key without a valid user session exposes no protected rows.

## Phase 5 - Migrate application features locally

### Goal

Replace SQLite JDBC stores with authenticated API-backed implementations while retaining domain and service rules.

### Suggested order

1. Authentication and profiles.
2. Member administration.
3. Memberships and payments.
4. Visits.
5. Announcements.
6. Expenses.
7. Workouts and workout sets.
8. Body metrics.
9. Dashboard summaries.
10. Account-management and destructive operations.

### Per-feature workflow

1. Add the required migration, function, grants, and RLS policies.
2. Add database authorization and constraint tests.
3. Implement the Java API client behind a narrow interface.
4. Adapt service and UI code without leaking transport types into the domain layer.
5. Run unit, integration, and JavaFX tests.
6. Compare behaviour with the existing SQLite implementation.
7. Complete the feature gate before starting the next feature.

Operations such as creating a Member, initial Membership, and initial Payment must remain atomic. They must use one
database transaction or protected server operation rather than several independent client requests.

## Phase 6 - Provision the live project

### Goal

Create an isolated hosted environment only after the local implementation and security tests pass.

### Steps

1. Create a hosted Supabase project in the Singapore region.
2. Store its database password in a password manager.
3. Link the repository tooling to the correct hosted project.
4. Review and deploy the same migrations used locally.
5. Keep public signup disabled.
6. Create the first Owner through a controlled administrative procedure.
7. Deploy protected account-creation functions.
8. Configure the release application with only the live URL and publishable key.
9. Configure custom SMTP before relying on live password-reset or invitation email.

### Sensitive actions requiring explicit approval

- Signing in to the hosting provider and completing MFA.
- Creating or deleting a hosted project.
- Entering or revealing a database password, account password, or payment information.
- Deploying schema or functions to the live project.
- Importing real personal or financial data.
- Resetting, deleting, or overwriting live data.
- Upgrading to a paid service.

The user should enter credentials and MFA codes directly. Secrets must not be pasted into chat, terminal output, source
files, or commit history.

## Phase 7 - Add deployment safeguards

### Goal

Prevent local test actions from affecting the live environment.

### Controls

- Provide distinct `runLocal`, verification, production packaging, and production smoke-test workflows.
- Make `runLocal` reject a non-local URL.
- Make local reset reject non-loopback hosts.
- Display the target project before applying remote migrations.
- Do not provide a production seed command.
- Do not expose a general-purpose live `Reset All` control.
- Protect any unavoidable destructive administrative action with server-side authorization, recent re-authentication,
  explicit scope, and typed confirmation.
- Fail a release build configured with a local URL.
- Back up live data before each migration that can affect stored records.

No workflow should run a linked remote database reset as a routine development action.

## Phase 7A - Add controlled co-owner provisioning

### Goal

Allow the initial Owner to provision additional Owners for the real gym without exposing public Owner signup or
granting Members any path to elevate their role.

### Steps

1. Replace the single-Owner database constraint with explicit invariants that require at least one active Owner.
2. Add an Owner-only protected operation for creating or inviting another Owner.
3. Require recent re-authentication before adding, deactivating, or changing the role of an Owner.
4. Prevent an Owner from demoting or deactivating the final active Owner.
5. Record who initiated every Owner creation, activation, deactivation, and role change.
6. Add an Owner-management screen that clearly distinguishes Owners from Members.
7. Keep all Owner provisioning unavailable to signed-out users and Members.

### Tests and gate

- The initial Owner can provision a second Owner, who can sign in on a separate installation.
- A Member cannot create, promote, update, or deactivate an Owner by modifying client requests.
- The final active Owner cannot be demoted, deactivated, or deleted.
- Concurrent Owner changes cannot leave the gym without an active Owner.
- Owner-management audit records identify the actor, target, action, and timestamp.
- Database, RLS, service, UI, and Checkstyle tests pass before the single-Owner constraint is removed from production.

## Phase 8 - Run live smoke and isolation tests

### Goal

Verify cross-device sharing and authorization before importing real data.

### Steps

1. Create one fake live Owner and two fake live Members.
2. Sign in as the Owner on one installation and Member A on another.
3. Change Member A's Membership as the Owner and verify Member A sees the change.
4. Create an allowed workout or metric as Member A and verify the Owner sees it.
5. Verify Member B cannot access Member A's records.
6. Close and reopen both applications to test session handling.
7. Interrupt network access to test recovery and user-facing errors.
8. Exercise concurrent writes and uniqueness conflicts from two machines.
9. Remove fake live records through the controlled administrative path.

### Gate

- All cross-device updates are visible as expected.
- Every RLS isolation test still passes against the hosted project.
- No secret credentials appear in the release JAR or logs.
- Service interruption does not corrupt local UI state or live records.

## Phase 9 - Migrate legacy SQLite data

### Goal

Move verified legacy data without treating SQLite as the ongoing source of truth.

### Steps

1. Freeze changes in the old application and take a final SQLite backup.
2. Run a purpose-built, read-only migration utility against the backup.
3. Create or invite live Auth identities for the Owner and Members.
4. Map old numeric account identifiers to Auth UUIDs.
5. Import parent records before dependent records.
6. Validate all foreign keys and constraints after import.
7. Compare table counts, financial totals, and representative histories with the recorded baseline.
8. Require Owner acceptance before distributing the cloud release.
9. Retain the SQLite backup for an agreed recovery period.

Existing password hashes will not be copied into Supabase Auth. Members will receive temporary credentials or use a
password setup/reset flow.

## Phase 10 - Ongoing promotion workflow

Every change follows this sequence:

1. Create and apply the change locally.
2. Rebuild the local database from an empty state.
3. Run database, RLS, service, and UI tests.
4. Run Checkstyle and the full Gradle verification suite.
5. Manually test the local JavaFX application with fake Owner and Member accounts.
6. Review migrations and the live target.
7. Back up live data.
8. Deploy the reviewed migrations and functions.
9. Package the production application.
10. Run a limited live smoke test without changing unrelated real data.

Before handoff, run at least:

```text
gradlew.bat checkstyleMain
gradlew.bat check
```

Gradle caches must remain outside the repository.

## Free-tier operational limits

The hosted Free project is suitable for coursework, demonstrations, and limited production-readiness testing, but it
is not a high-availability commercial service.

- A low-activity free project may be paused and require manual resumption.
- Free projects require regular manual logical backups.
- Default authentication email delivery is intended for testing and is rate-limited.
- Free-tier quotas, availability, support, and terms can change.
- There is no production service-level agreement on the free plan.

Monitor storage, authentication users, API traffic, egress, project activity, and backup success. Define an upgrade
threshold before real usage depends on uninterrupted availability.

## Completion criteria

- Any clean installation starts with universal sign-in rather than forced Owner creation.
- Owners and Members can use separate installations against the same live gym data.
- Local and live environments use the same versioned PostgreSQL schema and authorization rules.
- The local environment can be rebuilt without manual dashboard changes.
- Members cannot access another Member's protected data even with modified requests.
- No privileged credential is present in the desktop release, logs, or Git history.
- Live deployment and destructive actions require explicit targeting and safeguards.
- Legacy data is reconciled and retained in a recoverable backup.
- Database tests, Java tests, Checkstyle, and staged cross-device tests pass.

## Reference documentation

- [Supabase local CLI setup](https://supabase.com/docs/guides/local-development/cli/getting-started)
- [Supabase local development workflow](https://supabase.com/docs/guides/local-development/cli-workflows)
- [Supabase database testing and linting](https://supabase.com/docs/guides/local-development/cli/testing-and-linting)
- [Supabase API key security](https://supabase.com/docs/guides/getting-started/api-keys)
- [Supabase Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [Supabase password authentication](https://supabase.com/docs/guides/auth/passwords)
- [Supabase available regions](https://supabase.com/docs/guides/platform/regions)
- [Supabase database backups](https://supabase.com/docs/guides/platform/backups)
- [Supabase free project pausing](https://supabase.com/docs/guides/platform/free-project-pausing)
