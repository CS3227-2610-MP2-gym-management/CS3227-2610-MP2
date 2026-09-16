# Dylan's Contribution Record

> Verification status: Pending Dylan review

This document tracks Dylan's feature and team-level work. Update it with commit and pull-request evidence after each
feature is merged.

## Completed features

| Area | Contribution | Evidence |
| --- | --- | --- |
| Initial UI | Login, Owner Home, and Member Home JavaFX previews with single-window navigation | `adfd335` |
| Owner authentication | First-run Owner setup, secure login and logout, one-Owner constraint | `871ea27`, PR #1 |
| Application reset | Password-and-`RESET` guarded transactional database reset and Owner-sidebar access | `871ea27`, `3e8c1dc`, PR #8 |
| Code cleanup | Removed test-only persistence API and low-value reflection test | `b6c168a`, PR #1 |
| Member management | Atomic onboarding, Singapore profile validation, search, full-page profiles, payment history, and in-page editing | `3d4b995`, PR #2 |
| Membership management | Membership overview, renewal Payments, activation controls, validity contract, and schema migration | `c95711e`, PR #3 |
| Visit oversight | All/current Visit review, per-Member history, and current visitor count | `732dc64`, PR #4 |
| Member password reset | Owner-authorized Member credential replacement with fresh PBKDF2 salts | `65871d7`, PR #5 |
| Visit correction | Owner correction of Visit timestamps with latest reason and responsible Owner | `2b7c76a`, PR #6 |
| Payments overview | Read-only global Payment ledger with Member search and Membership links | `596ac0d`, PR #6 |
| Owner overview | Live Member, Membership, all-time financial, visitor, and recent-Member summaries | `3a26cb7`, PR #6 |
| Expense management | Immutable operating Expenses, category filtering, Finances tabs, and all-time net summary | `091d31b`, `593b36a`, `ad3b355`, PR #7 |
| Announcement management | Owner publication, withdrawal, active notices, and retained history | `e1603c8`, PR #8 |
| Application themes | Persistent light/dark mode across screens and dialogs | `feature/potatoad88-dark-mode` |
| Record-card interface | Virtualized, responsive card lists replacing dense tables across Owner and Member screens | `feature/potatoad88-ui-improvements` |

## Team-level engineering

| Area | Contribution | Evidence |
| --- | --- | --- |
| Build and packaging | Java 25 Gradle build and four platform-specific JavaFX JARs | `adfd335`, `871ea27` |
| Continuous integration | Cross-platform checks and JAR artifacts plus CodeQL workflow | `adfd335` |
| Shared persistence | SQLite initialization, centralized schema, account storage, atomic reset | `871ea27` |
| Automated testing | Authentication, password hashing, persistence, rollback, and packaging checks | `871ea27` |
| Agentic SE | Superpowers planning/TDD workflow and Ponytail Full review | Dylan logs and reflections |

## Planned team-level work

- Documentation and AI-log updates in every feature branch

Do not list Member-facing features here unless Dylan actually implements them and the team agrees that ownership has
changed.
