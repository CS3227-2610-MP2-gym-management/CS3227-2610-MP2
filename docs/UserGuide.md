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

Owner Home currently displays placeholder overview cards. Select `Members` to open Member management, or
`Memberships` to review all purchased Membership periods. Select
`Create Member` to open the creation form directly; after a successful creation, GymFlow opens the Members page.
Payments and Visits remain visible previews and are not yet functional.

## Managing Members

The Members page lists each Member's number, name, email, and phone number. Enter all or part of a Member's name or
email and select `Search`, or press Enter in the search field. A blank search displays every Member again.

### Creating a Member

1. Select `Create Member`.
2. Enter the Member email, initial password, profile information, membership dates, and payment information.
3. Re-enter the initial password and select `Create Member`.

The password must contain 12–128 characters. GymFlow currently supports Singapore phone numbers only: enter eight
digits beginning with `3`, `6`, `8`, or `9`; the fixed `+65` prefix is stored automatically. Date of birth is optional,
but a supplied date must show that the Member is at least 12 years old. Membership expiry cannot precede its start, and
the SGD payment must be positive with at most two decimal places.

GymFlow assigns the next number such as `M000001`. Account, profile, Membership, and Payment creation succeed together;
an error creates none of them. Validation and storage errors appear in the form without closing it or clearing entered
values. Give the initial password to the Member securely outside GymFlow.

### Editing a Member

Select a Member row to open the Member profile page. The profile page shows the Member number, contact details,
Membership history, and read-only Payment history. Select `Back to Members` to return to the list.

Select `Edit` on the profile page to update email, full name, phone number, and optional date of birth. The edit view
keeps payment history visible but read-only. Select `Cancel` to discard changes or `Save Changes` to persist them.
Phone numbers and dates of birth follow the same validation rules as creation. The Member number cannot be changed. A
validation failure remains on the edit page for correction.

## Managing Memberships

The Memberships page lists every purchased period with its Member, start date, expiry date, and derived status. Search
by Member name or email, select `Search`, or press Enter. Clearing the search displays all Memberships again.

From a Member profile, select `Add Membership` to record another access period and its Payment. Dates are selected from
the calendar controls. The suggested start is the day after the latest active period expires, or today when none
exists; the suggested expiry is one month later. Enter a positive SGD amount, choose `CASH`, `CARD`, or `TRANSFER`, and
optionally enter a reference.

Active Membership periods cannot overlap. A deactivated period may be replaced by a new overlapping period. Select a
Membership row and use `Deactivate` to stop it granting gym access without disabling the Member account or removing
history. A current or future deactivated period may be reactivated when it does not overlap another active period.
Expired periods cannot be reactivated; add a new Membership instead.

Statuses are calculated from the active flag and dates: `ACTIVE`, `UPCOMING`, `EXPIRED`, or `DEACTIVATED`.

## Member dashboard preview

Member authentication is not implemented. `Preview Member Dashboard` opens a static preview containing placeholder
membership, visit, and profile information. Its entry and exit buttons do not record data.

## Resetting GymFlow

`Reset GymFlow` on Owner Home permanently deletes the Owner account and every record stored in the GymFlow database.

1. Select `Reset GymFlow`.
2. Enter the current Owner password.
3. Enter the exact confirmation text `RESET`.
4. Confirm the reset.

Incorrect confirmation or password leaves the database unchanged and keeps the confirmation window open for
correction. A successful reset returns to first-launch Owner setup. This operation is irreversible and does not create
a backup.

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
