# Production Deployment

## Hosted environment

The production-ready test environment is an isolated Supabase Free project:

| Setting | Value |
| --- | --- |
| Organization | `GymFlow` |
| Project | `GymFlow Production` |
| Project reference | `ixbhtfqsznxteurqmguw` |
| Region | Southeast Asia (Singapore) |
| API URL | `https://ixbhtfqsznxteurqmguw.supabase.co` |

The database password is held outside the repository. The CLI is linked through the developer's local Supabase
profile; its access token and temporary connection details must not be copied into documentation or commits.

## Deployed baseline

The hosted database has migrations `20260928010000` through `20260928092000`. The `manage-member` Edge Function is
deployed and requires a valid session. Migration `20260928080000` adds a service-role-only operation for the one-time
initial Owner bootstrap. It derives the email from the selected Auth identity and refuses to run unless the
application account table is empty. Migration `20260928090000` adds protected co-owner creation and activation,
current-password re-authentication, active-Owner invariants, and Owner-account audit records.
Migration `20260928091000` gives only the protected server role access to allocate Member numbers; desktop and
signed-out callers retain no direct sequence access. Migration `20260928092000` runs the service-role-only atomic
Member creation operation with its function owner's privileges, without granting direct table writes to the server
role or desktop clients.

The first active Owner Auth identity and GymFlow account have been provisioned. The Owner email and password are not
stored in this repository. That Owner can use the application's `Owners` page to provision co-owners. The new Owner's
real email and temporary password are entered only in the application; the acting Owner must confirm the change with
their current password. No privileged key is shipped to the desktop client.

Production Auth is configured with:

- Public signup, anonymous sign-in, and manual identity linking disabled.
- Email confirmation enabled.
- A 12-character minimum password with lower-case, upper-case, digit, and symbol requirements.

The default Supabase email service is not suitable for operational password recovery or invitations. Configure and
test custom SMTP before onboarding real Members or relying on email recovery.

## Release client configuration

Production clients receive only the HTTPS API URL and publishable key. Start from
`config/production.env.example`, obtain the project's current **publishable** key from **Project Settings > API
Keys**, and set the three values in the launching process environment. Do not use either the secret key or the legacy
`service_role` key in the desktop application.

The publishable key is not an authorization secret. Database grants, Row Level Security, and the signed-in user's
access token enforce access. Nevertheless, avoid printing the key unnecessarily and never print user access or
refresh tokens.

## Deployment verification

Before and after a production deployment:

1. Run `npm run supabase:reset`, `npm run supabase:test`, and `npm run supabase:lint` locally.
2. Run `gradlew.bat verifyLocal` with both Gradle cache locations outside the repository.
3. Set `GYMFLOW_CONFIRM_PRODUCTION_PROJECT=ixbhtfqsznxteurqmguw` only after checking the displayed target, then use
   `npm run supabase:push:production`. The guarded command verifies the linked project, performs a dry run, creates a
   timestamped logical backup, and only then applies migrations.
4. Keep backups under `work/production-backups` on encrypted storage. They contain Auth and application data, are
   ignored by Git, and must not be uploaded or committed.
5. Deploy only reviewed Edge Functions.
6. Set the production client variables, run `gradlew.bat productionSmokeTest`, and then run
   `gradlew.bat releaseJars`. Both commands reject missing, loopback, insecure, or privileged-key configuration.
7. Sign in with the production Owner account and confirm that the Owner dashboard loads.

Never run `supabase db reset --linked`. Production test data and eventual real data must be removed only through an
explicit, reviewed administrative procedure. Free projects can pause after inactivity and do not provide a
production service-level agreement, so resume checks and manual logical backups are operational requirements.
