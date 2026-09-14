# GymFlow User Guide

GymFlow is a JavaFX desktop application for managing a small gym. This guide describes only features present in the
current release.

## Requirements

- Java SE 25
- A supported Windows x64, Linux x64, macOS x64, or macOS ARM64 computer

## Starting GymFlow

Download the JAR matching your operating system and processor architecture, then run it from a terminal:

```shell
java -jar GymFlow-macos-arm64.jar
```

Replace the filename with the JAR downloaded for your platform. GymFlow stores its local database at
`data/gymflow.db`, relative to the directory from which it is launched.

## First-launch Owner setup

When no Owner exists, the opening screen displays `Set up GymFlow`.

1. Enter the Owner's email address.
2. Enter a password containing between 12 and 128 characters.
3. Enter the same password in the confirmation field.
4. Select `Create Owner Account`.

Email addresses are matched without regard to letter case. After successful setup, GymFlow opens Owner Home.

## Owner login and logout

On subsequent launches, enter the Owner email and password and select `Sign In`. Invalid credentials display
`Invalid email or password` without identifying which value was incorrect.

Select `Log out` in the Owner sidebar to clear the current session and return to Login.

## Owner Home

Owner Home currently displays placeholder overview cards and an empty member table. The Members, Memberships,
Payments, and Visits navigation buttons are visible previews and are not yet functional.

## Member dashboard preview

Member authentication is not implemented. `Preview Member Dashboard` opens a static preview containing placeholder
membership, visit, and profile information. Its entry and exit buttons do not record data.

## Resetting GymFlow

`Reset GymFlow` on Owner Home permanently deletes the Owner account and every record stored in the GymFlow database.

1. Select `Reset GymFlow`.
2. Enter the current Owner password.
3. Enter the exact confirmation text `RESET`.
4. Confirm the reset.

Incorrect confirmation leaves the database unchanged. A successful reset returns to first-launch Owner setup. This
operation is irreversible and does not create a backup.

## Testing from source

Run the application:

```shell
./gradlew run
```

Run automated checks:

```shell
./gradlew clean check
```

Build all supported release JARs:

```shell
./gradlew releaseJars
```
