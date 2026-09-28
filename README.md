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

## Run locally

```shell
npm install
npm run supabase:start
```

Keep the Edge Function runtime open in a second terminal:

```shell
npm run supabase:functions
```

Then launch GymFlow from a third terminal:

```shell
./gradlew runLocal
```

On Windows, use `gradlew.bat runLocal` for the final command.

## Test

```shell
npm run supabase:test
npm run supabase:lint
./gradlew verifyLocal
```

## Build platform JARs

```shell
./gradlew releaseJars
```

`releaseJars` is a production task. It requires the HTTPS production URL, publishable key, and explicit production
environment selection described in the [Production Deployment guide](docs/ProductionDeployment.md).

The generated Windows x64, Linux x64, macOS x64, and macOS ARM64 JARs are placed in `release/`. Run the JAR matching
the operating system and processor architecture:

```shell
java -jar release/GymFlow-macos-arm64.jar
```

GitHub Actions deploys the product website from `site/` and runs a scheduled availability check against the live URL.
