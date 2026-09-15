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

Owner Home displays a current visitor count alongside placeholder cards for features not yet connected to data.
Select `Members` to open Member management, `Memberships` to review all purchased Membership periods, or `Visits` to
review attendance. Select
`Create Member` to open the creation form directly; after a successful creation, GymFlow opens the Members page.
Select `Payments` to review the gym's recorded membership payments.

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
Membership history, Visit history, and read-only Payment history. Select `Back to Members` to return to the list.

Select `Edit` on the profile page to update email, full name, phone number, and optional date of birth. The edit view
keeps payment history visible but read-only. Select `Cancel` to discard changes or `Save Changes` to persist them.
Phone numbers and dates of birth follow the same validation rules as creation. The Member number cannot be changed. A
validation failure remains on the edit page for correction.

### Resetting a Member password

The Owner sets and confirms the initial password while creating a Member and must communicate it securely outside
GymFlow. To replace it later, open the Member profile, select `Reset Password`, and enter the new password twice. The
new password must contain 12–128 characters. Validation or storage errors remain in the dialog so they can be
corrected without reopening it.

The Owner does not need to enter their own password again because this action is available only inside an authenticated
Owner session. A successful reset replaces the old Member password without changing the Member's profile, account
activity, Memberships, Payments, or Visits. Member login routing remains under development; the temporary Member
preview is still available until that work is complete.

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

If a Membership is deactivated while the Member is already inside the gym, their ongoing Visit remains open. They
remain listed under `Currently Visiting` and included in the current visitor count until they submit an exit. GymFlow
does not create an artificial exit time; deactivation only prevents the Membership from authorizing a future entry.

Statuses are calculated from the active flag and dates: `ACTIVE`, `UPCOMING`, `EXPIRED`, or `DEACTIVATED`.

## Reviewing Payments

The Payments page lists every recorded membership Payment with its Member, Membership period, SGD amount, method,
payment time, and optional reference. Search by Member name or email and select `Search`, or press Enter. Clearing the
search displays every Payment again. This page is read-only; Payment editing, deletion, refunds, and exports are not
available.

## Reviewing Visits

The Visits page provides two tabs. `All Visits` contains completed and ongoing visits, while `Currently Visiting`
contains only Members whose Visit has no exit time. Search by Member name or email and select `Search`, or press Enter.
Clearing the search restores all records in the selected tab.

Each row shows the Member number and name, entry time, exit time, and duration. An ongoing Visit displays
`Currently inside` and `Ongoing`. Times use the computer's local time zone. The same read-only history is available
from the Member's profile page.

To correct a record, select it and choose `Correct Selected Visit`. The Owner may change its entry time and may add,
change, or clear its exit time. Every correction requires a reason. Clearing the exit marks the Member as currently
inside and is rejected if that Member already has another open Visit. Previous correction information is shown when
the record is corrected again; GymFlow retains the latest correction rather than a complete audit history. Owners
cannot create new Visits directly.

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
