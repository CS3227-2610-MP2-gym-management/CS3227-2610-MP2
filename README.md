# GymFlow

GymFlow is a JavaFX desktop application for a small gym. It provides separate experiences for gym owners and gym
members.

The current version supports local Owner account setup and login, Member onboarding and profiles, Membership periods,
income and expenses, Visit oversight and correction, gym announcements, and guarded factory reset. The Member
dashboard remains a development preview while Member features are added separately.

Use the theme control at the top of the window to switch between light and dark mode. GymFlow remembers the selected
theme for future launches on the same computer.

Project documentation:

- [User Guide](docs/UserGuide.md)
- [User Stories](docs/UserStories.md)
- [Developer Guide](docs/DeveloperGuide.md)
- [Agentic SE Reflections](docs/Reflections.md)
- [AI Interaction Logs](logs/README.md)

Product website: [GymFlow on GitHub Pages](https://cs3227-2610-mp2-gym-management.github.io/CS3227-2610-MP2/)

## Owner setup and login

On the first launch, GymFlow asks you to create the installation's single Owner account. Enter an email address and a
password between 12 and 128 characters. GymFlow opens Owner Home after successful setup.

On later launches, sign in with the same Owner email and password. Email matching is case-insensitive. Use
`Return to Login` in the Owner sidebar to clear the current session.

Member authentication is not implemented yet. The Login screen therefore retains a clearly labelled Member dashboard
preview temporarily.

GymFlow stores local application data in `data/gymflow.db`, relative to the directory from which the application is
launched. Passwords are salted and hashed; plain-text passwords are not stored.

Basic diagnostic monitoring writes rotating files under `data/logs/gymflow-0.log` through
`data/logs/gymflow-2.log`. These
files record startup and sanitized unexpected-error types, not passwords or form contents, and are ignored by Git.

## Reset GymFlow

Every Owner sidebar includes `Reset GymFlow` for returning the installation to a clean state. Enter the current Owner
password and the exact confirmation text `RESET`.

This action is irreversible. It removes the Owner account and every database-backed gym record, including Members,
Memberships, Payments, Expenses, Visits, and Announcements. After reset, GymFlow returns to first-launch Owner setup.

## Requirements

- Java SE 25

## Run locally

```shell
./gradlew run
```

On Windows, use `gradlew.bat run`.

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
