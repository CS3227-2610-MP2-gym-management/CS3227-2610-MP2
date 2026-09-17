# Dylan's Agentic SE Reflection

## How I customized the agent

I used one Codex agent throughout my work on GymFlow. I customized its behaviour through detailed prompts and selected
existing skills according to the task. Superpowers supported requirements clarification, planning, test-driven
development, systematic debugging, and verification. Ponytail Full acted as a minimalism review after larger changes,
looking for unnecessary production APIs, speculative abstractions, and low-value tests.

I also repeatedly supplied the MP2 project description, grading criteria, Owner and Member user stories, and shared
entity definitions as authoritative context. This helped the agent preserve the boundary between my Owner-facing work
and my teammate's Member-facing work. It also kept non-code requirements such as documentation, interaction logs,
CI/CD, monitoring, and GitHub Pages visible during development.

This requirements context was provided through prompts and planning sessions; it was **not** implemented as a custom
GymFlow skill. A project-specific skill has not been committed to this repository. If I repeated the project, I would
turn the recurring requirements and completion checks into a tested traceability skill instead of repeatedly pasting
them into conversations.

## Three skills in detail

### 1. Brainstorming and planning: deciding the feature boundary before coding

**Purpose and selection.** I used brainstorming and planning when a request contained product decisions rather than
only an obvious code change. Authentication was a good example: "Owner login" also required decisions about first-run
setup, local persistence, password security, reset behaviour, packaging, and the ownership boundary with Member-side
development.

**How it was applied.** Before implementation, the agent and I agreed that one local installation would support one
Owner account, that the Login screen would become first-run setup when no Owner existed, and that Reset GymFlow would
require both the current Owner password and the exact text `RESET`. Later plans used the supplied user stories and
entities to expose shared contracts, such as Membership validity and Visit storage, without implementing my teammate's
Member screens or entry/exit workflow.

**Evidence and outcome.** The decisions and subsequent corrections are summarized in the
[Owner-authentication interaction log](../../../logs/dylan/2026-09-14-owner-authentication.md), while the initial role
and entity discussion appears in the [initial UI log](../../../logs/dylan/2026-09-14-initial-ui.md). Planning made
cross-role boundaries explicit before database and UI work began and reduced the chance of implementing my teammate's
scope accidentally.

**Limitations and correction.** The authentication slice became much larger than I first expected because the plan
uncovered hashing, persistence, transactional reset, platform packaging, and tests. This was useful discovery, but
future plans should identify hidden scope earlier and split a large feature into smaller reviewable milestones. The
agent also tended to produce plans that were more detailed than necessary, so I had to keep emphasizing exclusions and
minimal interfaces.

### 2. Test-driven development and verification: protecting data and business rules

**Purpose and selection.** I used test-driven development for security, persistence, schema migrations, and business
rules because failures in these areas are difficult to detect through visual testing and can corrupt data. Verification
before completion was used to require fresh evidence before commits or success claims.

**How it was applied.** Password tests covered randomized salts, exact Unicode and whitespace handling, valid and
invalid credentials, Owner uniqueness, and clearing supplied password arrays. Temporary SQLite databases exercised
reopening, foreign keys, schema migrations, reset rollback, Membership overlap, one open Visit per Member, and the rule
that an exit cannot precede entry. Release verification also built the platform-specific JARs rather than assuming that
an application that ran through Gradle would package correctly.

**Evidence and outcome.** The
[Membership-management log](../../../logs/dylan/2026-09-14-membership-management.md) records how a failing legacy-
database test drove an idempotent timestamp migration. The
[Visit-oversight log](../../../logs/dylan/2026-09-15-visit-oversight.md) records the database constraints used by both
roles. These tests provided stronger evidence for destructive and persistence-heavy behaviour than manual happy-path
testing alone.

**Limitations and correction.** Passing tests did not guarantee correct JavaFX behaviour. During authentication UI
integration, the agent initially risked reading control values from a background thread. Reviewing the complete data
flow moved control reads back to the JavaFX Application Thread while leaving hashing and database access in background
tasks. Manual JAR testing also exposed repeated text clipping, dialog sizing, spacing, and navigation problems that the
automated suite could not see. In future, I would define a repeatable visual checklist—including minimum window size,
light and dark themes, scrolling, and error states—before UI implementation begins.

### 3. Ponytail Full: removing generated complexity after correctness was established

**Purpose and selection.** I used Ponytail Full after substantial changes to ask whether every new API, abstraction,
and test justified its maintenance cost. This complemented correctness review: the goal was not merely to make the code
pass, but to remove code that existed only because the agent had generated it.

**How it was applied.** In the authentication slice, Ponytail identified `AccountStore.setActive(...)`, which existed
only to arrange a test, and an `AppViewTest` that checked API shape rather than user-visible behaviour. Both were
removed. The inactive-account case was still tested by arranging the temporary database directly. Later features
followed the same principle: Visit correction retained only the latest correction metadata instead of creating a full
audit subsystem; expenses were kept separate from Member Payments; announcements used `withdrawnAt` without read
tracking; and card lists reused JavaFX `ListView` virtualization instead of creating every card in a `VBox`.

**Evidence and outcome.** The authentication cleanup is recorded in the
[Owner-authentication log](../../../logs/dylan/2026-09-14-owner-authentication.md). The
[card-list interaction log](../../../logs/dylan/2026-09-16-card-list-interface.md) records the later decision to reuse
one renderer while keeping domain-specific content in each screen. These reviews reduced speculative code while
preserving the tested behaviour.

**Limitations and correction.** The agent sometimes generated extra structure first and removed it only after review.
A better instruction would require every production method to have a production caller and every abstraction to solve
an existing variation. Minimalism also cannot be applied before the requirements are understood: removing code too
early can hide security, migration, or error-handling needs. I learned to establish correctness and scope first, then
use Ponytail to simplify the resulting design.

## What the agent handled effectively

- It translated detailed user stories and entity definitions into concrete schemas, service operations, validation
  rules, tests, and Owner-facing workflows.
- It generated repetitive persistence and test scaffolding efficiently, especially for SQLite mappings and temporary-
  database cases.
- It helped maintain consistency across code, documentation, contribution records, and interaction logs after each
  feature.
- It was effective at comparing alternatives when the request contained an ambiguous product decision, such as
  whether deactivation should affect login or whether Visit corrections required a full audit history.
- It responded well to concrete screenshots and reproduction steps during UI review.

## Where the agent created additional work

- It repeatedly produced JavaFX labels whose text clipped at runtime. Fixes sometimes addressed one screen without
  consistently applying the established clipping-safe style elsewhere.
- It occasionally over-engineered a small requirement with test-only APIs, speculative flexibility, or excessive plan
  detail.
- Automated tests and Checkstyle could pass while dialog layout, scrolling, spacing, and minimum-size behaviour still
  required several rounds of manual correction.
- Long tasks accumulated documentation and reflection text that became feature chronology rather than focused evidence,
  requiring a later editing pass such as this one.

These cases show that the agent was most productive when I supplied an explicit boundary, observable acceptance
criteria, and a final human review step. Broad requests such as "make it modern" or "check everything" produced more
iteration than targeted instructions tied to a screenshot, failing test, or named workflow.

## What I would change next time

1. Create and test a project-specific traceability skill at the start. It would map each user story to the responsible
   role, implementation, tests, documentation, and interaction log while checking the submission rubric.
2. Add a JavaFX visual-review skill covering text clipping, minimum dimensions, scrolling, keyboard behaviour, light
   and dark themes, loading, empty, validation, and failure states.
3. Require production methods to have production callers and require proposed abstractions to solve a current need.
4. Break large feature plans into smaller approval checkpoints so hidden persistence, packaging, and documentation work
   becomes visible earlier.
5. Keep a concise evidence entry after each task instead of reconstructing feature history near submission.
6. Continue requiring fresh verification before commits, while recording clearly which behaviours were automated and
   which were checked manually.

## What I learned about designing one effective agent

An effective software-engineering agent is not defined by a long persona or complete autonomy. It needs authoritative
context, narrow task modes, explicit exclusions, observable completion criteria, and human-controlled handoffs. The
most useful skills named both what to do and when to stop: plan before changing an ambiguous workflow, use failing tests
for business rules, debug from evidence, review unnecessary complexity after correctness, and verify before claiming
completion.

The agent improved speed and breadth, but it did not replace judgement. I remained responsible for deciding product
rules, protecting my teammate's scope, evaluating visual quality, approving commits, and deciding whether verification
was sufficient. The closed loop—define the boundary, let the agent implement, inspect the evidence, correct the result,
and record the lesson—was more reliable than either unstructured prompting or unquestioned automation.
