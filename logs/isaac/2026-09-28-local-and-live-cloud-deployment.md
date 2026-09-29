# Local and Live Cloud Deployment

**Conversation start:** 28 September 2026, 02:05 Singapore time  
**Status:** Completed
**Branch:** `feature/itzxitzx-deploy-for-production`

## Goal and outcome

The initial request was to let a first-time installer sign in as either an Owner or Member, instead of forcing every
installation to create an Owner. The conversation expanded this into a safe local test environment and an isolated
hosted production environment.

The completed application now opens on one sign-in screen, stores shared data in Supabase, supports controlled
co-owner provisioning, and uses the same PostgreSQL migrations and authorization rules locally and in production.
The original SQLite database remains only for regression tests and reference. Its records were confirmed to be demo
data and were not imported.

This is a comprehensive interaction summary, not a verbatim transcript or a record of private model reasoning.
Passwords, tokens, database credentials, key values, and disposable test credentials are deliberately excluded. The
real Owner email is also omitted because it is not needed to reproduce the work.

## Conversation chronology

1. At 02:05, Isaac explained that first launch forced Owner creation even though the installer could be a Member. He
   asked for free cloud database options, verified setup instructions, guided computer control, warnings, and staged
   tests.
2. Isaac revised the requirement to keep a local version for testing while adding a live version for production
   readiness. The plan adopted two isolated Supabase environments with one schema and authorization model.
3. Isaac asked to save the plan under `docs/Plans`; `local-and-live-cloud-deployment.md` was added.
4. Isaac requested phased implementation, a passing test gate before every commit, repository-style commit messages,
   periodic updates, and questions only for actions or information that required him.
5. Isaac confirmed Docker login. Local Supabase setup, schema translation, Auth integration, RLS, and feature-store
   migration proceeded through Phases 0–5.
6. Isaac asked to pause. Work stopped in a recoverable state and later resumed at his request.
7. During hosted setup, Isaac supplied short confirmations such as `done`, `neither?`, and `yes` while Supabase setup
   screens were open. The screen state was rechecked instead of guessed. Isaac entered the database password himself
   because it was sensitive and only he could provide it.
8. Isaac asked that permission be requested only for things he alone could do or provide. Later non-sensitive,
   in-scope setup and verification continued without repeated confirmation.
9. Isaac asked whether the first Owner email could be dummy or had to be real, supplied a real email, and clarified
   that actual gym owners must later be provisioned as co-owners. The retained initial account used the real address;
   acceptance-test accounts were disposable.
10. Isaac asked where `Add user` was in the Supabase dashboard, created the Auth user, and confirmed that Owner Home
    loaded. The application Owner was bootstrapped through a one-time protected operation.
11. Isaac asked what the production-backup safeguard meant and whether `work/production-backups` would appear in a
    later clone. The directory is ignored by Git, so its plaintext backups stay on this computer and are absent from
    clones. Isaac approved that location for now.
12. Isaac confirmed later sign-ins and Owner-card loading while co-owner provisioning was tested. A second app process
    proved that the co-owner could authenticate independently.
13. Isaac approved live sharing tests and specified one active and one upcoming Membership. Two disposable Members
    were used to verify shared records and cross-Member isolation.
14. A test login was rejected after the disposable Owner was deactivated. Isaac asked why deletion was proposed and
    whether the real Owner would remain. It was clarified that the real Owner was not a cleanup target; an inactive
    fake Owner could retain an Auth identity and audit history unless the explicitly disposable identity was removed.
    Isaac approved the cleanup needed to complete the phase.
15. Isaac confirmed that SQLite contained demo data that did not need migration. Phase 9 was closed with a documented
    no-import decision rather than copying fake records into production.
16. Isaac asked whether all phases were complete. Phases 0–10 were confirmed complete, with custom SMTP left as an
    operational prerequisite before real Member onboarding or reliance on email recovery.
17. Isaac requested a final documentation audit, clear testing references, and this comprehensive interaction log,
    including technical issues and decisions encountered along the way.
18. While preparing the PR, Isaac asked how other developers could test without his personal production credentials.
    The local committed `.test` accounts were confirmed as the normal review path, and a separate fake-data staging
    project was recommended for hosted testing.
19. Isaac chose to allow a trusted developer to test live co-owner behaviour through a temporary production account.
    The guides were updated to require a separate reviewer identity, backup, full-access acknowledgement, a fixed test
    window, secure credential delivery, and deactivation after review. His retained Owner credentials remain private.

## Platform and architecture decision

Supabase was selected because one free platform supplies PostgreSQL, managed authentication, an HTTPS Data API, Row
Level Security, protected Edge Functions, a local Docker development stack, and a hosted project. A database-only
host would still require a separately designed authentication and authorization service.

```text
Local development                         Hosted production
JavaFX desktop client                     Release JavaFX desktop client
        |                                         |
Local Supabase via Docker                 Supabase Free project, Singapore
        |                                         |
Same migrations, grants, RLS, functions, and role model
```

The desktop client receives only its environment's URL and publishable key. Database passwords and service-role or
secret keys are never shipped in the JAR. Supabase Auth owns passwords; PostgreSQL RLS and protected operations own
data authorization.

## Phase results and commits

| Phase | Result and verification gate | Commit |
| --- | --- | --- |
| 0 | Preserved and reconciled the SQLite baseline before changing persistence. | `95077f7` |
| 1 | Added reproducible local Supabase startup, reset, tests, lint, seed, and safe loopback defaults. | `e25853d` |
| 2 | Translated the schema to ordered PostgreSQL migrations with constraints and database tests. | `0c91642` |
| 3 | Replaced first-run Owner setup with universal Supabase Owner/Member sign-in and role routing. | `373ec99` |
| 4 | Added grants and RLS; verified Owner access, Member self-access, cross-Member denial, and signed-out denial. | `1b37e86` |
| 5 | Moved application features to authenticated Supabase stores and protected multi-record operations. | `9508e70` |
| 6 | Created and secured the hosted Singapore project, deployed migrations/function, and bootstrapped the real Owner. | `0e347e3` |
| 7 | Added explicit local/production configuration, guarded promotion, backups, and production smoke/release checks. | `b91ef56` |
| 7A | Added separate co-owner accounts, password re-authentication, final-active-Owner protection, and audit. | `71b8bda` |
| 8 fixes | Corrected Member-number allocation and hardened atomic Member creation after live-only gaps appeared. | `d738c6c`, `23862b3` |
| 8 | Verified live sharing, role isolation, duplicate handling, deactivation, cleanup, and retained Owner state. | `124e380` |
| 9 | Recorded that legacy SQLite contains demo data and must not be imported. | `50b83f6` |
| 10 | Confirmed no pending migrations and passed production smoke plus all four release-JAR checks. | `66aee44` |

Every implementation phase was committed only after its relevant automated or live gate passed. The final hosted
baseline contains migrations through `20260928092000` and the deployed `manage-member` Edge Function.

## Issues discovered and corrections

| Issue | Cause or risk | Resolution and evidence |
| --- | --- | --- |
| First installation forced Owner creation | SQLite treated each computer as a separate gym | Replaced setup routing with universal shared sign-in. |
| Local and live data could be confused | Both environments use similar APIs | Added `runLocal`, a local badge, loopback-only reset, HTTPS production checks, and explicit project confirmation. |
| Local tests had changed seed data | Earlier integration work left the local backend dirty | Reset local Supabase before the clean phase gate. |
| Earlier tests assumed exactly one Owner | Product requirement changed to co-owners | Updated schema, services, UI, tests, and final-active-Owner invariant. |
| An inactive co-owner produced an internal exception | RLS hid the account row after Auth accepted the identity | Authentication now returns the normal rejected-login result. |
| A rollback test could not remove the final Owner | The new invariant blocked old cleanup assumptions | Isolated rollback fixtures and used test-only truncation for teardown. |
| A Member-number test assumed `M0004` | Sequence state legitimately advances across tests | Asserted the current sequence value instead of a fixed number. |
| Live Member creation lacked sequence permission | Local service-role execution masked a production grant gap | Migration `20260928091000` restricted allocation to the protected server role. |
| Retrying exposed missing table privilege | A broad grant would weaken RLS boundaries | Migration `20260928092000` made atomic creation service-role-only and security-definer. |
| A shell count appeared to show one Membership | PowerShell collapsed a single response shape | Direct Owner and Member queries confirmed active and upcoming rows. |
| Initial cleanup failed on a Payment foreign key | A disposable Payment still referenced the fake Owner | The transaction rolled back; corrected cleanup audited and removed only the test dependencies. |
| The dashboard Run click did not initially execute SQL | The browser action did not submit the editor | The query was submitted with the dashboard keyboard action and checked. |
| Production tests could leave fake identities | Auth and audit history are intentionally durable | Backed up, filtered by exact disposable identities, retained the real Owner, and removed only test data. |

The first failed cleanup did not partially delete data because it ran in a transaction. After corrected cleanup,
production contained one active Owner, one application account in total, and no remaining Phase 8 Auth identities.

## Decisions retained

- A clean installation always shows sign-in; it never creates an Owner locally.
- Owners and Members use separate Auth identities and may sign in from different computers.
- Co-owners are supported, but the final active Owner cannot be deactivated.
- Public signup, anonymous sign-in, and manual identity linking are disabled.
- Production passwords require at least 12 characters with lowercase, uppercase, digit, and symbol.
- Member creation and privileged Auth changes use protected server operations; no secret key enters the client.
- RLS protects Member-owned data even if a caller bypasses the JavaFX screens.
- Local mode is for disposable tests. Production is for controlled readiness and later real use.
- Production reset is prohibited as a routine workflow. Promotions perform a dry run and backup first.
- `work/production-backups` is plaintext local storage ignored by Git. It is absent from clones and should be kept on
  encrypted storage for real operations.
- The known SQLite data is demo-only and is not migrated.
- Custom SMTP must be tested before depending on invitations or password-recovery email.
- A free project can pause and has no production SLA; backups, resume checks, quota monitoring, and an upgrade
  threshold remain operational responsibilities.

## Live acceptance evidence

- The real Owner loaded Owner Home from the hosted environment.
- A disposable co-owner was created after password confirmation and signed in from a second process.
- Two disposable Members were created; Member A saw one active and one upcoming Membership.
- A Member A body-mass reading was visible to an Owner but invisible to Member B.
- Two concurrent writes for the same Member/date returned one success and one conflict, leaving one row.
- Deactivating the disposable co-owner rejected a new login.
- A pre-cleanup logical backup was created; disposable identities and their records were removed.
- A final migration dry run reported production up to date.
- `productionSmokeTest` and all four platform release-JAR validation tasks passed.

## Documentation and tools

The README, architecture, user guide, developer guide, decisions, user stories, production guide, deployment plan, and
this log describe the final system and its verification paths. Standard repository, Gradle, Docker/Supabase CLI, SQL,
and browser-control workflows were used; no reusable Codex skill was invoked. Isaac alone entered sensitive account
and database passwords and created the initial Auth identity when required. No credential values were committed.

## Remaining operational work

The implementation phases are complete. Before onboarding real Members, configure and test custom SMTP, decide a
backup retention and restore drill, monitor free-tier limits and inactivity, and define when to move to a paid service
level.
