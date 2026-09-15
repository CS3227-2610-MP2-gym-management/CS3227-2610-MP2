# GymFlow

GymFlow is a JavaFX desktop application for a small gym. It provides separate experiences for gym owners and gym
members.

The current version supports local Owner account setup and login plus Owner-managed Member onboarding, profiles,
Membership periods, and Payments. The Member dashboard remains a development preview while Member features are added
separately.

Project documentation:

- [User Guide](docs/UserGuide.md)
- [Developer Guide](docs/DeveloperGuide.md)
- [Agentic SE Reflections](docs/Reflections.md)
- [AI Interaction Logs](logs/README.md)

## Owner setup and login

On the first launch, GymFlow asks you to create the installation's single Owner account. Enter an email address and a
password between 12 and 128 characters. GymFlow opens Owner Home after successful setup.

On later launches, sign in with the same Owner email and password. Email matching is case-insensitive. Use `Log out`
in the Owner sidebar to clear the current session and return to Login.

Member authentication is not implemented yet. The Login screen therefore retains a clearly labelled Member dashboard
preview temporarily.

GymFlow stores local application data in `data/gymflow.db`, relative to the directory from which the application is
launched. Passwords are salted and hashed; plain-text passwords are not stored.

## Reset GymFlow

Owner Home includes `Reset GymFlow` for returning the application to a clean testing state. Enter the current Owner
password and the exact confirmation text `RESET`.

This action is irreversible. It removes the Owner account and every database-backed gym record, including members,
memberships, payments, visits, and workouts. After reset, GymFlow returns to first-launch Owner setup.

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
