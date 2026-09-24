# Project Decisions and Architecture Interaction Summary

## Goal

Review the planning document, current implementation, and project documentation to identify unresolved design
decisions, record the decisions Isaac made, and create a concise architecture contract for subsequent AI coding
agents implementing the complete GymFlow backlog.

## Prompt and interaction summary

- Isaac supplied `CS3227 MP2 planning.docx` containing prioritized Owner and Member stories, proposed entities, field
  rules, and discussion comments.
- Isaac asked which implementation details still required agreement beyond decisions already present in Dylan's code,
  with Java SE 25 desktop delivery required now and shared cloud data anticipated later.
- The repository and planning document were reviewed separately: document content and comments were treated as
  planning context, while implemented code and maintained project documents were used to identify settled behaviour.
- The review identified unresolved areas covering cloud boundaries, identity and roles, concurrent writes, offline
  policy, authoritative time, membership/payment lifecycle, audit history, data lifecycle, evolution, validation, and
  operational testing.
- Isaac placed cloud architecture KIV until the local application is complete, retained one Owner, selected online-only
  operation for a future shared deployment, and described in-person Membership purchase recorded by the Owner.
- Isaac requested that the agreed details be written into the repository.
- Isaac then requested a whole-project `ARCHITECTURE.md`, based on Project Decisions, User Stories, Developer Guide,
  User Guide, and the current implementation, for future AI coding agents to follow.
- Finally, Isaac requested this interaction summary under `logs/isaac` using the existing Dylan log style.

## Skills and tools

- The document skill was used to read and distinguish the attached DOCX's content from the user's actual request.
- PowerShell and OOXML inspection extracted the planning document's paragraphs, tables, and comments without editing
  the source document.
- Repository search and inspection covered Gradle configuration, package structure, schema and migrations, services,
  authentication, CI workflows, User Stories, User Guide, Developer Guide, and existing interaction-log conventions.
- `apply_patch` was used for all repository documentation changes.

## Agreed product decisions

- Cloud architecture remains KIV. The team will finish the local Java SE 25 desktop application before selecting a
  remote API, server framework, cloud database, or related infrastructure.
- One installation represents one gym with one Owner. Additional administrator and staff roles remain out of scope.
- A future shared deployment is online-only. Offline write queues, synchronization, and disconnected conflict
  resolution are out of scope.
- Account identity/login state and Membership entry entitlement are separate concepts.
- A prospective Member may self-register, but the planned account initially awaits Owner approval and cannot enter the
  gym.
- Membership payment occurs in person and is manually confirmed and recorded by the Owner; GymFlow does not process
  online payments.
- First approval should activate the account and create its Membership and Payment atomically.
- After first activation, Membership expiry removes gym-entry eligibility but does not deactivate the account. The
  Member may still log in and view permitted information.
- Administrative account deactivation prevents login independently of Membership history.
- Every purchase or renewal creates a new Membership and Payment rather than modifying an old purchase.
- Active Membership periods may not overlap, purchase details must preserve historical price/plan information, and
  Membership deactivation neither creates a refund nor closes an existing Visit.
- Check-in requires an active account, a currently valid Membership, and no open Visit. Check-out remains allowed
  after Membership expiry or deactivation.

## Important recommendations and open decisions

- The existing account `active` boolean cannot distinguish pending approval from deliberate administrative
  deactivation. An explicit account status such as `PENDING`, `ACTIVE`, and `DEACTIVATED` was recommended, but the
  exact representation remains open and must be agreed before self-registration is implemented.
- Refunds, instalments, voids, payment corrections, authoritative gym timezone, concurrent-write policy, idempotency,
  audit scope and retention, data deletion and recovery, remote compatibility, and shared error contracts remain open.
- Future cloud work should normally place a server-side API between desktop clients and the cloud database, but this
  was recorded only as a recommendation because the architecture decision is KIV.
- New local work should keep business rules out of JavaFX and JDBC-specific code so a later remote boundary remains
  practical without introducing speculative abstractions now.

## Documentation and architecture work

- Added `docs/ProjectDecisions.md` as the living record of agreed, KIV, and open decisions.
- Added the Project Decisions link to `README.md` and `docs/DeveloperGuide.md`.
- Added repository-level `ARCHITECTURE.md` as the implementation contract for current and planned features.
- Added the Architecture link to `README.md` and `docs/DeveloperGuide.md`.
- Defined document authority, system context, layer responsibilities, allowed dependency direction, domain invariants,
  transaction boundaries, authorization rules, validation levels, time and money conventions, migration requirements,
  cloud-readiness constraints, testing expectations, prohibited shortcuts, the current/planned implementation map, and
  an AI-agent handoff checklist.
- Preserved the existing concrete service/store approach and required new interfaces only for a real second
  implementation, external boundary, or useful test seam.
- Distinguished today's Owner-created atomic onboarding from the planned self-registration and Owner-approval flow.
- Corrected the Architecture file location to the repository root so it matches its README link and stated role.

## Review and verification

- The planning DOCX was read without modification.
- Documentation links were checked against their target paths.
- `git diff --check` reported no whitespace errors; Git only reported the repository's existing LF-to-CRLF conversion
  warning for tracked Markdown files.
- No production Java, database schema, dependencies, or tests were changed during this interaction.
- Automated application tests were not run because the changes were documentation-only.

## Affected components

- `ARCHITECTURE.md`
- `docs/ProjectDecisions.md`
- `docs/DeveloperGuide.md`
- `README.md`
- `logs/isaac/2026-09-18-project-decisions-and-architecture.md`

## Human review status

Pending Isaac review. Isaac should confirm that the recorded registration and first-activation lifecycle matches the
team's intent, especially whether pending Members may log in and the final account-status representation.
