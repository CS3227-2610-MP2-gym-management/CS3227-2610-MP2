# GymFlow

GymFlow is a JavaFX desktop application for a small gym. It provides separate experiences for gym owners and gym
members.

The current version supports shared Owner and Member sign-in, co-owner provisioning, Member onboarding and profiles,
Membership periods, income and expenses, Visit oversight and correction, and gym announcements. Members can check in
to start a Workout, edit notes and exercises, check out, and review completed Workouts. Owners can export the currently
displayed Member, Income Payment, and Visit records as CSV files.

Use the theme control at the top of the window to switch between light and dark mode. GymFlow remembers the selected
theme for future launches on the same computer.

Project documentation:

- [Architecture](ARCHITECTURE.md)
- [User Guide](docs/UserGuide.md)
- [User Stories](docs/UserStories.md)
- [Agreed Project Decisions](docs/ProjectDecisions.md)
- [Developer Guide](docs/DeveloperGuide.md)
- [Agentic SE Reflections](docs/Reflections.md)
- [AI Interaction Logs](logs/README.md)

Product website: [GymFlow on GitHub Pages](https://cs3227-2610-mp2-gym-management.github.io/CS3227-2610-MP2/)

## Owner and Member login

Every installation opens on the same sign-in screen. Owners and Members use credentials provisioned for the shared
gym. GymFlow loads the authenticated account's role and opens the corresponding dashboard. Use `Return to Login` in
the sidebar to end the current session.

Authentication and shared feature data are provided by Supabase Auth, PostgreSQL, the Data API, and protected Edge
Functions. SQLite remains only for legacy-data reference and isolated regression tests; the running application does
not read or write `data/gymflow.db`.

Basic diagnostic monitoring writes rotating files under `data/logs/gymflow-0.log` through
`data/logs/gymflow-2.log`. These
files record startup and sanitized unexpected-error types, not passwords or form contents, and are ignored by Git.

The former installation-wide `Reset GymFlow` action is unavailable because one local installation must not erase
shared gym data. Production deletion and restoration require an explicitly scoped, backed-up administrative process.

## Requirements

- Java SE 25
- Docker Desktop for the local Supabase development backend
- Node.js and npm

## Quick-start testing

Run commands from the repository root. Local mode uses disposable seeded data and displays a `LOCAL DEVELOPMENT`
badge. Live mode connects to the shared production database, so restrict it to the sign-in and Home-page smoke test
unless a broader test has been approved.

### Local development - Windows

Start Docker Desktop, then open PowerShell:

```powershell
npm install
npm run supabase:start
npm run supabase:reset
```

Keep that backend running. In a second PowerShell window, run the Edge Function:

```powershell
npm run supabase:functions
```

In a third PowerShell window, launch GymFlow:

```powershell
.\gradlew.bat runLocal
```

### Local development - macOS

Start Docker Desktop, then open Terminal:

```shell
npm install
npm run supabase:start
npm run supabase:reset
```

Keep that backend running. In a second Terminal window, run the Edge Function:

```shell
npm run supabase:functions
```

In a third Terminal window, launch GymFlow:

```shell
./gradlew runLocal
```

The seeded local test accounts are:

| Role | Email | Password |
| --- | --- | --- |
| Owner | `owner.local@example.test` | `LocalOwner!2026` |
| Member | `member.a.local@example.test` | `LocalMemberA!2026` |

### Live production - Windows

You need the production publishable key and your own provisioned GymFlow account from the maintainer. In PowerShell,
replace the key placeholder, verify the connection, build, and launch from the same window:

```powershell
$env:GYMFLOW_ENV = "production"
$env:GYMFLOW_SUPABASE_URL = "https://ixbhtfqsznxteurqmguw.supabase.co"
$env:GYMFLOW_SUPABASE_PUBLISHABLE_KEY = "replace-with-publishable-key"
.\gradlew.bat productionSmokeTest
.\gradlew.bat releaseJars
java -jar .\release\GymFlow-windows-x64.jar
```

Sign in, confirm that the correct Home page loads, and stop. After closing GymFlow, close PowerShell or clear the
configuration:

```powershell
Remove-Item Env:GYMFLOW_ENV
Remove-Item Env:GYMFLOW_SUPABASE_URL
Remove-Item Env:GYMFLOW_SUPABASE_PUBLISHABLE_KEY
```

### Live production - macOS

You need the production publishable key and your own provisioned GymFlow account from the maintainer. In Terminal,
replace the key placeholder, verify the connection, build, and launch from the same window:

```shell
export GYMFLOW_ENV=production
export GYMFLOW_SUPABASE_URL=https://ixbhtfqsznxteurqmguw.supabase.co
export GYMFLOW_SUPABASE_PUBLISHABLE_KEY=replace-with-publishable-key
./gradlew productionSmokeTest
./gradlew releaseJars
java -jar release/GymFlow-macos-arm64.jar
```

The example is for Apple silicon. On an Intel Mac, launch `release/GymFlow-macos-x64.jar` instead. Sign in, confirm
that the correct Home page loads, and stop. After closing GymFlow, close Terminal or run:

```shell
unset GYMFLOW_ENV GYMFLOW_SUPABASE_URL GYMFLOW_SUPABASE_PUBLISHABLE_KEY
```

Never put production keys or account credentials in a tracked file, and never use a secret or legacy `service_role`
key. `runLocal` intentionally cannot launch the live version. See the
[Production Deployment guide](docs/ProductionDeployment.md#test-the-live-application-from-windows) for safeguards
and temporary reviewer access.

## Automated local checks

```shell
npm run supabase:test
npm run supabase:lint
./gradlew verifyLocal
```

On Windows, use `.\gradlew.bat verifyLocal`. The complete role and feature sequence is in the
[User Guide manual acceptance checklist](docs/UserGuide.md#manual-acceptance-checklist).

GitHub Actions deploys the product website from `site/` and runs a scheduled availability check against the live URL.
