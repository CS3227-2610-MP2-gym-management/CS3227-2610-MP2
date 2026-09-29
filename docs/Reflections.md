# Agentic SE Reflections

This is the canonical team reflection submitted for GymFlow. The individual source reflections remain available for
traceability: [Dylan's reflection](reflections/dylan/Reflections.md) and
[Isaac's reflection](reflections/isaac/Reflection.md). Each section below preserves its author's decisions, evidence,
limitations, and learning. Both authors reviewed their attributed sections.

## Team synthesis

Both of us used one Codex agent as a supervised engineering collaborator. We gave it the project requirements, user
stories, shared entities, repository instructions, and task-specific acceptance criteria; we then used planning,
tests, verification, and interaction summaries to keep later sessions consistent. This customization came from
prompts, plans, repository instructions, and installed workflows. We did **not** create a custom GymFlow skill.

Our emphasis differed. Dylan repeatedly used Ponytail Full after correctness work to challenge speculative APIs,
test-only production methods, and unnecessary abstractions. Isaac relied especially on systematic cross-layer
debugging as GymFlow expanded across JavaFX, Java services, Supabase Auth, PostgreSQL functions, RLS, Edge Functions,
and CI. Together these approaches encouraged both simplicity and evidence-based diagnosis.

We encountered the same limits. Generated JavaFX screens still needed human review for clipping, density, scrolling,
and theme behaviour. Local success did not always predict cloud behaviour involving tokens, grants, clocks,
sequences, and shared state. Detailed plans improved traceability but sometimes preserved an assumption too early or
became longer than the decision required. Our strongest workflow was therefore iterative: agree on scope, prototype
risky assumptions, implement a small slice, verify at the relevant layers, inspect the real interface or backend, and
write discoveries back into shared project artifacts.

## Dylan Wong Kooi Fung

### Customizing the agent

I customized one Codex agent through detailed prompts and selected existing workflows. Superpowers supported
requirements clarification, planning, test-driven development, systematic debugging, and verification. Ponytail Full
acted as a minimalism review after larger changes. Repeatedly supplying the MP2 description, rubric, role boundaries,
stories, and entity definitions helped the agent keep my Owner-facing work separate from Isaac's Member-facing work
and remember non-code deliverables such as documentation, logs, CI, monitoring, and the product website.

### Skill 1: planning the feature boundary

I used brainstorming and planning when a request contained product decisions rather than a single obvious edit. For
the original Owner authentication work, the agent and I made decisions about setup, password security, reset,
packaging, and the boundary with Member development before implementation. Later plans exposed shared Membership and
Visit contracts without adding Isaac's Member screens. The
[Owner-authentication log](../logs/dylan/2026-09-14-owner-authentication.md) and
[initial UI log](../logs/dylan/2026-09-14-initial-ui.md) record those decisions.

This reduced accidental scope overlap, but it also revealed more persistence and packaging work than expected. Some
plans became more detailed than necessary. I learned to split large changes into approval checkpoints, state explicit
exclusions, and prototype uncertain workflows before specifying every implementation detail.

### Skill 2: tests and verification for business rules

I used failing tests for authentication, persistence, migrations, transactions, and business rules because visual
testing could not reliably expose data corruption. Tests covered password behaviour, database constraints, reset
rollback, Membership overlap, one open Visit per Member, timestamp ordering, and platform JAR structure. The
[Membership-management log](../logs/dylan/2026-09-14-membership-management.md) records how a legacy-database test drove
an idempotent migration, while the [Visit log](../logs/dylan/2026-09-15-visit-oversight.md) records constraints shared
across both roles.

For CSV export, a failing test exposed spreadsheet-formula injection: Singapore phone numbers begin with `+`. The
exporter consequently protects `+`, `=`, `-`, and `@`, as well as quoting commas, quotation marks, line breaks, and
Unicode. This was stronger evidence than a visual spreadsheet check alone.

Passing tests still did not prove JavaFX usability. Manual JAR testing repeatedly found clipped labels, dialog sizing,
spacing, navigation, and scrolling issues. I learned to pair automated checks with a visual checklist covering minimum
size, both themes, keyboard use, loading, empty, validation, and failure states.

### Skill 3: Ponytail Full after correctness

I used Ponytail Full to ask whether every new production API, abstraction, and test justified its maintenance cost.
It identified an account-activation method used only to arrange a test and a reflection test that checked API shape
rather than behaviour; both were removed while the actual inactive-account rule remained tested. Later design choices
followed the same principle: Visit correction stores only the latest correction metadata, expenses remain distinct
from Member Payments, announcements use withdrawal rather than read tracking, and record cards reuse virtualized
JavaFX lists. The [card-list log](../logs/dylan/2026-09-16-card-list-interface.md) records that reuse decision.

Minimalism was most useful after scope and correctness were established. Applying it too early could remove necessary
authorization, migration, or error handling. Next time I would require every production method to have a production
caller and every abstraction to solve an existing variation before it is added.

### Evaluation and learning

The agent translated stories into schemas, services, validation, tests, and Owner workflows efficiently and kept code,
guides, contributions, and logs aligned. It was particularly useful for repetitive persistence scaffolding and for
comparing ambiguous product choices. It also created extra work through overly broad plans, occasional speculative
structure, and repeated JavaFX clipping defects that passed automated checks.

I would create a small tested traceability workflow at the start, maintain concise evidence after each feature, and
define a reusable visual-review checklist. The main lesson is that an effective agent needs authoritative context,
narrow modes, explicit exclusions, observable completion criteria, and human-controlled handoffs. I remained
responsible for product rules, role boundaries, visual quality, commit approval, and deciding whether evidence was
sufficient.

## Isaac

### Customizing the agent

I customized one Codex agent through detailed prompts, staged plans, and repository instructions. Dylan and I first
agreed on stories, priorities, and shared entities, then recorded them in project documents such as
`docs/UserStories.md` and `ARCHITECTURE.md`. `AGENTS.md` made Checkstyle and external Gradle-cache hygiene persistent
requirements. I selected verification according to risk: service and database tests for rules, rendered and manual
checks for JavaFX, and migrations, RLS, integration tests, CI, and controlled live checks for Supabase.

### Skill 1: requirements clarification and dependency planning

I used planning when stories did not settle workflow details. The agent compared planning documents, code, stories,
and guides, then separated confirmed decisions from open ones. This established rules such as in-person Membership
payments, one historical Membership and Payment per renewal, expiry blocking entry rather than login, and check-out
remaining possible after Membership expiry. The
[architecture log](../logs/isaac/2026-09-18-project-decisions-and-architecture.md) and
[Member plan log](../logs/isaac/2026-09-18-gym-member-plan.md) record this work.

The limitation was that a detailed plan could precisely encode a wrong assumption. The initial Workout design treated
Visits and Workouts separately; using the application showed that one gym session should connect both. The later
[unification plan](../logs/isaac/2026-09-27-unified-workout-visits-plan.md) changed schema, services, Owner UI, Member
UI, and tests. I would prototype the riskiest end-to-end assumption before writing a complete plan.

### Skill 2: layered testing with UI verification

I used service tests for Member isolation, open-session rules, Workout ordering, body-mass rules, passwords, and
rollback; database tests for authorization and constraints; rendered JavaFX tests for stable layout; and manual use
for subjective interaction. Checkstyle became a repository rule after early Workout code produced many violations.
When ordinary tests missed wrapping and scrolling defects, the project gained a real-window `renderedUiTest` task.

The [Workout UI log](../logs/isaac/2026-09-25-member-workout-ui.md),
[body-mass log](../logs/isaac/2026-09-26-member-body-mass.md), and
[rendered-test log](../logs/isaac/2026-09-24-member-renewal-guidance-and-rendered-ui-tests.md) show how broad visual
requests became concrete changes. Human review still found an unreadable dark-mode calendar label, unwanted
double-click interaction, duplicate actions, and unclear set entry. I learned to specify hierarchy, action count,
minimum size, themes, and all loading and error states before implementation.

### Skill 3: systematic cross-layer debugging

I used evidence-based debugging when failures crossed JavaFX, Java services, HTTP authentication, PostgreSQL
functions, RLS, and CI. The procedure was to start from observable evidence, trace state across boundaries, identify
the first violated assumption, apply the smallest consistent fix, add a regression test, and disclose any remaining
verification gap.

This found that navigation erased a Workout draft because cloud Visit and Workout state had diverged, and that a
Member email update failed because the next profile request reused an old token. The
[Workout-state log](../logs/isaac/2026-09-28-member-home-workout-state-regression.md),
[session-refresh log](../logs/isaac/2026-09-28-supabase-session-refresh.md), and
[deployment log](../logs/isaac/2026-09-28-local-and-live-cloud-deployment.md) record the causes, regression coverage,
and controlled production checks.

Other defects showed why local green tests were insufficient: PostgreSQL sequences do not reset when rows are
deleted, foreign keys determine cleanup order, business dates differ from server session dates, and direct backend
requests must enforce the same Membership rules as the UI. These cases taught me to inspect the actual key, clock,
authorization rule, and state transition rather than treating an HTTP status or nearest failing line as the cause.

### Evaluation and learning

The agent generated repetitive Java, SQL, mappings, and fixtures quickly while preserving layers and traceability. It
also automated JUnit, Checkstyle, rendered UI, pgTAP, integration, CI, and release checks. Additional work came from
vague visual prompts, stale assumptions in long plans, local/cloud differences, and fixes that initially crossed more
layers than necessary.

Next time I would prototype risky domain assumptions, isolate integration data from the beginning, use shorter phase
checkpoints, and turn only the stable completion checklist into a small tested workflow. The agent needs a hierarchy
of context: stable decisions in architecture and stories, task criteria in prompts, and observations in follow-up
corrections. My role remained to decide product rules, judge UI quality, challenge stale assumptions, and control live
credentials, destructive actions, and cleanup.
