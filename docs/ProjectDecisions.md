# GymFlow Agreed Project Decisions

This document records product and architecture decisions agreed by the team. It complements the user stories: the
stories describe what users need, while this document defines the rules and constraints that implementations should
follow. Update this document whenever the team changes a decision or resolves an open question.

## Decision status

- **Agreed** means the team intends to implement and preserve the decision.
- **KIV** means the decision is intentionally deferred and must not be treated as settled.
- **Open** means the team still needs to agree on the behaviour before implementing the affected feature.

## Agreed scope and architecture

### Local application and future cloud deployment

**Status: KIV**

GymFlow will be completed as a local Java SE 25 desktop application before the team chooses its cloud architecture.
The eventual deployment should allow multiple Members and the Owner to use shared data concurrently, but the choice
between a server API and other deployment designs is deferred. New code should continue to keep business rules out of
JavaFX views so that a future persistence or network boundary remains practical.

### Gym and Owner model

**Status: Agreed**

- One GymFlow installation represents one gym.
- The system has one Owner account.
- Additional administrator, manager, or staff roles are outside the current scope.

### Connectivity

**Status: Agreed for a future shared deployment**

The application will be online-only when it eventually uses shared remote data. Offline writes, queued synchronization,
and conflict resolution for disconnected clients are outside the current scope. The local development version may
continue using its local SQLite database without a network connection.

## Accounts, Memberships, Payments, and gym access

### Separate account and Membership concepts

**Status: Agreed**

An account controls identity and login. A Membership controls entitlement to enter the gym. These are separate:

- A Member may have an account without a currently valid Membership.
- An expired or deactivated Membership does not deactivate the Member's account.
- A Member without a valid Membership may log in and view permitted information, but may not check in or enter the gym.
- Account deactivation is reserved for an administrative or security reason, such as a banned account or compromised
  credentials. An inactive account cannot log in, regardless of its Membership history.
- Gym-entry eligibility is derived from both account and Membership data; it is not stored as another permanent status.

A Member may enter only when the account is active and at least one Membership satisfies all of these conditions on
the gym's current date:

```text
account.active
AND membership.active
AND membership.startDate <= today
AND today <= membership.expiryDate
```

The Member must also have no existing open Visit before checking in.

### Member registration and first activation

**Status: Agreed direction; implementation is not yet complete**

- A prospective Member may create an account without purchasing online.
- A newly self-registered account begins in a pending/inactive state and cannot log in or enter the gym.
- Membership purchase occurs in person at the gym counter.
- After confirming payment, the Owner activates the account and records the new Membership and its Payment.
- Account activation, Membership creation, and Payment creation should succeed or fail as one transaction so that a
  partially activated Member is not left behind.
- After this first activation, later Membership expiry does not make the account inactive. It only removes gym-entry
  eligibility until another valid Membership is purchased.

The current code supports Owner-created Member accounts rather than self-registration. Self-registration therefore
requires a separate implementation story and acceptance criteria before it is built.

The current `active` boolean cannot distinguish a newly registered account awaiting approval from an account that an
Owner deliberately disabled. Before self-registration is implemented, the recommended design is to replace or
supplement it with an explicit account status such as `PENDING`, `ACTIVE`, and `DEACTIVATED`. The exact status model is
**Open**, but the two conditions must not be presented to the Owner as if they mean the same thing.

### Member renewal guidance

**Status: Agreed and implemented for the current scope**

- A Member with no active or upcoming Membership receives an attention-level renewal notice on Member Home and the
  same in-person guidance on My Membership.
- The notice is derived from Membership history at screen load; it is not a persisted Member, Membership, or Payment
  status.
- The notice must be understandable without relying on color alone and must state that check-in is unavailable.
- It is informational only. It does not create a purchase request, Membership, Payment, or online-payment flow.
- An active Membership takes precedence over an upcoming Membership. An expiring-soon notice is outside current scope
  until its threshold and wording are agreed.

### Membership plans and purchases

**Status: Agreed direction; MembershipPlan remains planned**

- A MembershipPlan describes a reusable offering, including its name, duration, current price, and whether it may be
  sold.
- A Membership represents one purchased access period for one Member.
- Every purchase or renewal creates a new Membership record instead of extending or overwriting an earlier purchase.
- A purchase snapshots the plan and price information needed to preserve historical accuracy. Later edits to the plan
  must not change an earlier purchase.
- Active Membership periods for the same Member must not overlap.
- Deactivating a Membership prevents it from granting future entry but preserves its historical record.
- Deactivating a Membership while a Member is already inside does not manufacture an exit time or close the Visit.

### Payments

**Status: Agreed for the current scope**

- Payment is taken in person and recorded manually by the Owner; GymFlow does not process card or online payments.
- A Payment records the money received for a Membership purchase.
- The current scope uses exactly one positive Payment per Membership.
- Membership and Payment history is immutable during normal operation. A renewal creates another Membership and
  Payment.
- Payment recording must identify the Owner who recorded it and the time it was recorded.
- Currency remains fixed to SGD, with monetary values stored as integer cents.
- Deactivating a Membership does not automatically imply or create a refund.

Refunds, instalments, voided payments, transfers, and correction of an incorrectly recorded Payment remain **Open**.
If any of these are required, they should be modelled explicitly instead of editing historical amounts silently.

### Entry and exit

**Status: Agreed**

- Check-in requires an active account and a currently valid Membership.
- A Member may have at most one open Visit.
- Check-out requires an existing open Visit.
- Membership expiry or deactivation does not prevent a Member with an open Visit from checking out.
- An exit time cannot precede its entry time.

## Existing technical decisions

**Status: Agreed and implemented unless noted otherwise**

- The desktop application uses Java SE 25, JavaFX 25, Gradle, and SQLite through JDBC.
- The application follows UI, service, store, and database layers. JavaFX views do not issue SQL.
- Email addresses are normalized and unique without regard to case.
- Passwords are salted and hashed; plaintext passwords are not stored.
- Monetary amounts use integer SGD cents.
- Event timestamps use UTC `Instant` values; Membership periods use `LocalDate` values.
- Membership and Visit display statuses are derived rather than stored redundantly.
- Multi-record writes use transactions, and important integrity rules are also enforced by database constraints.
- Schema changes are versioned migrations.
- Announcements are withdrawn rather than deleted.
- Visit records retain the latest correction metadata. A complete administrative audit history is planned but not yet
  implemented.

## Open decisions

The following matters are not settled by this document and should be resolved before their affected features are
implemented:

1. Authentication sessions, password recovery, email verification, login rate limits, and whether the Owner needs
   multi-factor authentication in a shared deployment.
2. The explicit account statuses used to distinguish pending approval from administrative deactivation.
3. Behaviour when two users update the same record, including optimistic locking and duplicate-request protection.
4. The authoritative gym timezone and whether eligibility uses server time in a shared deployment.
5. Payment refunds, cancellations, voids, instalments, and correction procedures.
6. The exact administrative audit events and retention period.
7. Account deletion, anonymization, backup, restoration, and production reset policy.
8. Remote API and schema version compatibility after the cloud architecture is chosen.
9. The shared validation and error-response contract for future remote clients.
10. The required concurrency, authorization, migration, and operational tests for a shared deployment.

## Updating this document

For each new decision, record its status, the agreed rule, important exceptions, and any implementation that does not
yet conform. Do not silently infer a new product rule from whichever screen or database change happens to be written
first.
