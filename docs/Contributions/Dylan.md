# Dylan's Contribution Record

> Verification status: Pending Dylan review

This document tracks Dylan's feature and team-level work. Update it with commit and pull-request evidence after each
feature is merged.

## Completed features

| Area | Contribution | Evidence |
| --- | --- | --- |
| Initial UI | Login, Owner Home, and Member Home JavaFX previews with single-window navigation | `adfd335` |
| Owner authentication | First-run Owner setup, secure login and logout, one-Owner constraint | `871ea27`, PR #1 |
| Application reset | Password-and-`RESET` guarded transactional database reset | `871ea27`, PR #1 |
| Code cleanup | Removed test-only persistence API and low-value reflection test | `b6c168a`, PR #1 |

## Team-level engineering

| Area | Contribution | Evidence |
| --- | --- | --- |
| Build and packaging | Java 25 Gradle build and four platform-specific JavaFX JARs | `adfd335`, `871ea27` |
| Continuous integration | Cross-platform checks and JAR artifacts plus CodeQL workflow | `adfd335` |
| Shared persistence | SQLite initialization, centralized schema, account storage, atomic reset | `871ea27` |
| Automated testing | Authentication, password hashing, persistence, rollback, and packaging checks | `871ea27` |
| Agentic SE | Superpowers planning/TDD workflow and Ponytail Full review | Dylan logs and reflections |

## Planned Owner P0 work

- Member onboarding, search, and profile editing
- Membership activation, renewal, deactivation, and payment history
- Collective and per-Member attendance oversight
- Shared membership-validity contract for the teammate's Member entry workflow
- Documentation and AI-log updates in every feature branch

Do not list Member-facing features here unless Dylan actually implements them and the team agrees that ownership has
changed.
