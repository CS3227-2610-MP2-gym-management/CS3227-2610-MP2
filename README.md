# GymFlow

GymFlow is a JavaFX desktop application for a small gym. It provides separate experiences for gym owners and gym
members.

The current version supports shared Owner and Member sign-in, Member onboarding and profiles, Membership periods,
income and expenses, Visit oversight and correction, gym announcements, and guarded factory reset. Members can check in to start a Workout, edit notes and exercises, check out, and review completed Workouts. Owners can export the currently
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

Authentication is provided by Supabase Auth. During the staged cloud migration, feature records continue to use
`data/gymflow.db` until their stores are replaced by the shared PostgreSQL API.

Basic diagnostic monitoring writes rotating files under `data/logs/gymflow-0.log` through
`data/logs/gymflow-2.log`. These
files record startup and sanitized unexpected-error types, not passwords or form contents, and are ignored by Git.

The former installation-wide `Reset GymFlow` action is unavailable during cloud migration because deleting one local
installation must not erase or desynchronize shared gym data.

## Requirements

- Java SE 25
- Docker Desktop for the local Supabase development backend

## Run locally

```shell
./gradlew run
```

On Windows, start Docker Desktop, run `npm install` and `npm run supabase:start`, then use `gradlew.bat run`.

## Test

```shell
./gradlew clean check
```

## Build platform JARs

```shell
./gradlew releaseJars
```

The generated Windows x64, Linux x64, macOS x64, and macOS ARM64 JARs are placed in `release/`. Run the JAR matching
the operating system and processor architecture:

```shell
java -jar release/GymFlow-macos-arm64.jar
```

GitHub Actions deploys the product website from `site/` and runs a scheduled availability check against the live URL.
