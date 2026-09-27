# Cloud Migration Baseline

## Purpose

This document records the Phase 0 recovery and reconciliation baseline for the migration described in
[`local-and-live-cloud-deployment.md`](local-and-live-cloud-deployment.md). It contains aggregate values only and must
not contain Member personal data or credentials.

## Source snapshot

- Snapshot date: 28 September 2026
- Source: `data/gymflow.db`
- SQLite integrity check: `ok`
- Foreign-key violations: `0`
- SHA-256: `4925A8C16B44EBB3FE3088EBDD7F371DA7C6246755FBCD78DDAF5611A4DC94D5`
- Durable backup: `C:\Users\isaac\Documents\GymFlow-backups\phase-0-2026-09-28\gymflow.db`
- Backup integrity check: `ok`
- Backup SHA-256: `4925A8C16B44EBB3FE3088EBDD7F371DA7C6246755FBCD78DDAF5611A4DC94D5`

The source and backup hashes match. The backup is deliberately stored outside the repository and must not be staged.

## Aggregate reconciliation values

| Table | Row count |
| --- | ---: |
| `accounts` | 2 |
| `member_profiles` | 1 |
| `memberships` | 1 |
| `payments` | 1 |
| `expenses` | 0 |
| `announcements` | 0 |
| `workouts` | 4 |
| `workout_sets` | 9 |
| `body_metrics` | 2 |

| Financial measure | Amount in cents |
| --- | ---: |
| Payments | 10,000 |
| Expenses | 0 |

These values are the minimum reconciliation targets for the eventual legacy-data import. A new baseline must be
recorded after the old application is placed into read-only mode and immediately before the final production import.

## Phase 0 verification gate

- [x] The source SQLite database passes `PRAGMA integrity_check`.
- [x] The source SQLite database has no foreign-key violations.
- [x] A byte-identical backup exists outside the repository.
- [x] Aggregate row counts and financial totals are recorded without personal data.
- [x] The repository verification suite passes at the Phase 0 commit.
