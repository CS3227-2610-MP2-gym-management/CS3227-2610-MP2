# Isaac's Agentic SE Reflection

## How I customized the agent

I used one Codex agent throughout my Member-facing work and the later cloud migration. I customized it mainly through
detailed prompts, repository instructions, and staged plans rather than a custom skill. Before implementation, Dylan
and I discussed the user stories, feature priorities, and shared entities. These were recorded in the project planning
materials and `docs/UserStories.md`, which I then treated as authoritative context for the agent.

I selected the approach according to risk. Requirements work needed document and repository analysis; business rules
and persistence needed unit and database tests; JavaFX work needed rendered tests and manual visual review; Supabase
work needed migrations, Row Level Security (RLS), integration tests, CI, and controlled live checks. I also made
`ARCHITECTURE.md` an implementation contract and added `AGENTS.md` rules requiring Checkstyle and keeping Gradle
caches outside the repository. For each request, I normally named the user story or plan phase, required tests and
documentation, and specified whether the agent should stage or commit the result.

I did **not** create a reusable GymFlow skill. The document skill helped inspect the original planning DOCX, but the
recurring behaviour came from prompts, plans, and repository instructions. I checked that this customization worked by
observing whether later tasks preserved layer boundaries, followed feature dependencies, ran the required checks, and
updated the relevant stories and guides. If I repeated the project, I would formalize only these repeated checks as a
small workflow, rather than hide changing product decisions inside a large skill.

## Three skills in detail

The following are three interesting, repeatable agent skills from my work: requirements clarification and planning,
test-driven implementation with UI verification, and systematic cross-layer debugging. They were mostly workflows I
defined through prompts rather than installed `SKILL.md` packages. I consider them skills because each had a clear
trigger, procedure, expected output, and method of verification.

### 1. Brainstorming and planning: deciding the feature boundary before coding

**Purpose and selection.** I used planning when the existing stories described a goal but did not settle all
implementation behaviour. Dylan and I had already agreed on the main stories, entities, and priorities before his
Owner-facing work began, so my agent did not invent the backlog. I selected this skill when a request involved product
choices or several dependent features rather than a small, well-defined code change.

**How it was applied.** The agent compared the planning document, current code, stories, and guides, then asked me to
confirm unclear rules. These included Membership payments happening in person, every renewal creating a new historical
Membership and Payment, expiry blocking entry but not login, and check-out remaining possible after expiry or account
deactivation. It kept genuinely open matters, such as the final pending-account representation, separate. The Member
plan grouped 16 stories into ten dependency-ordered features with P0 and P1 gates. Every feature had ordered steps,
edge cases, English tests, and a Mermaid sequence diagram. I effectively defined the skill as: inspect authoritative
sources, expose contradictions, confirm rather than guess, then produce a dependency-ordered and testable plan.

**Evidence and outcome.** The [architecture log](../../../logs/isaac/2026-09-18-project-decisions-and-architecture.md)
separates agreed, recommended, and open decisions. The
[Member-plan log](../../../logs/isaac/2026-09-18-gym-member-plan.md) records ten feature sections, test sections, and
sequence diagrams. The diagrams made actors, authorization, service calls, and writes explicit, giving implementation
a clearer target than prose alone. Later features followed the P0-before-P1 order and did not accidentally introduce
online payment or treat Membership expiry as account deactivation.

**Limitations and correction.** Detail did not make a plan permanently correct. The first Workout plan required a
completed record with at least one set, allowed Member-entered times, and allowed deletion. After using the application,
I concluded that Visit and Workout were one session. The later
[unification plan](../../../logs/isaac/2026-09-27-unified-workout-visits-plan.md) changed the model: check-in opens a
Workout, check-out supplies its times, an empty Workout is valid, and Members cannot alter or delete attendance. This
required schema, service, Owner UI, Member UI, and test changes. Next time I would prototype the riskiest workflow
before producing a detailed plan. Sequence diagrams improve precision, but can precisely encode a wrong assumption.

### 2. Test-driven development and verification: protecting data and business rules

**Purpose and selection.** I used tests for authorization, validation, persistence, migrations, and transactions
because failures could expose another Member's data or corrupt records. I combined them with rendered JavaFX tests and
manual use because visual quality cannot be judged adequately from service tests. I selected this combined skill when
a feature crossed business logic, storage, and a visible Member workflow.

**How it was applied.** Tests covered Member authorization and isolation, one open session, Workout ordering, body-mass
date and value rules, password rules, and rollback. I defined a verification ladder: test rules at the service layer,
constraints at the database layer, stable layout with rendered tests, and subjective interaction through manual use.
Checkstyle became mandatory after the initial Workout code produced many violations. When ordinary tests missed text
wrapping and scrolling defects, the agent added a real-window `renderedUiTest` task.

I also asked for UI/UX best practices because some generated screens felt congested and unattractive. The first
Workout form used a raw timestamp and pipe-delimited set text. I requested date/time controls, exercise groups,
compact set rows, clearer hierarchy, and theme-aware actions. For body mass, I requested a graph and inline editor,
then reduced clutter by making daily entry primary and hiding date correction and custom-range controls until needed.

**Evidence and outcome.** The [Workout UI log](../../../logs/isaac/2026-09-25-member-workout-ui.md) records the move to
a calendar, 15-minute selectors, exercise groups, and compact rows. The
[body-mass log](../../../logs/isaac/2026-09-26-member-body-mass.md) records the progressively simplified workflow. The
[rendered-test log](../../../logs/isaac/2026-09-24-member-renewal-guidance-and-rendered-ui-tests.md) explains what was
automated and what remained manual. The skill worked when `renderedUiTest` caught a missing scroll-pane ID, while
manual review separately caught an unreadable dark-mode calendar label and unwanted double-click interaction.

**Limitations and correction.** “Use UI/UX best practices” was too broad to produce my intended design in one pass.
I changed calendar activation from double-click to single-click, requested explicit dark-mode colours, centred the
navigation, added a legend, and later moved that legend. The Workout dialog also needed fixes for duplicate Cancel
controls, blank Notes, and unclear set entry. My early prompt expressed dissatisfaction rather than observable
criteria. Next time I would specify the primary action, information priority, interaction count, reference screen,
minimum window size, themes, and empty/error states before implementation.

### 3. Systematic debugging: tracing failures across system boundaries

**Purpose and selection.** I used evidence-based debugging during the Supabase migration because the system crossed
JavaFX, Java services, HTTP authentication, PostgreSQL functions, RLS, and CI. I selected it when a defect appeared
only after navigation, in CI, or against a real backend. The agent had to trace the failure rather than edit the
nearest failing line.

**How it was applied.** I defined a fixed sequence: start from observable evidence, trace state across boundaries,
identify the first violated assumption, make the smallest consistent correction, add a regression test where the
defect escaped, and state any verification gap. When Home to Announcements to Home erased a Workout draft, the agent
found that the cloud backend had separated Visit and Workout state despite the unified local model. It restored one
open Workout as the source of truth. When CI failed after a Member email change, it found that the next profile read
used an old token. The correct order became: update identity, force session refresh, then reload account and profile.

**Evidence and outcome.** The [Workout-state log](../../../logs/isaac/2026-09-28-member-home-workout-state-regression.md)
and [session-refresh log](../../../logs/isaac/2026-09-28-supabase-session-refresh.md) show how failures became permanent
tests and guidance. The [deployment log](../../../logs/isaac/2026-09-28-local-and-live-cloud-deployment.md) records
phased gates, backups, role isolation, concurrent-write testing, and release checks. The Workout fix gained pgTAP
assertions, the session defect gained a cloud integration scenario, and CI became an independent database-and-Java
integration gate instead of silently skipping those tests.

**Limitations and correction.** Some failures needed realistic database state. A test expected Member number `M0004`,
but PostgreSQL sequences advance even when test rows are removed; the test should check uniqueness and current sequence
state, not a fixed number. Early code assumed one Owner, but later co-owner support required safeguards against removing
the final active Owner. Shared seed data also made later tests unreliable until the local database was reset. Finally,
production cleanup first failed because a Payment still referred to a disposable Owner. The transaction rolled back,
so nothing was partly deleted. The corrected process backed up the data, selected only exact test identities, removed
dependent records in order, and verified that the real Owner remained. Local tests were necessary but insufficient for
stateful cloud behaviour.

Later failures reinforced the need to locate the rule at every boundary. A Member without a current Membership could
still start tracking through both the UI and direct Supabase requests, so the fix covered button state, service
authorization, and database triggers rather than only the visible screen. A separate production failure came from a
date mismatch: the desktop used Singapore's calendar date while PostgreSQL used its session date. The fix made the
business date explicit in the database and tested the boundary. The body-mass HTTP 409 had a different cause: the
editor chose create or update from a cached reading for today even when a past date was selected. A date-keyed upsert
and a disabled Save button during requests corrected that workflow. These cases taught me to check the actual key,
clock, and authorization rule before treating an HTTP status as the root cause.

## What the agent handled effectively

- It converted agreed stories into models, services, database rules, screens, tests, and documentation.
- It generated repetitive Java, SQL, mappings, and fixtures quickly while preserving layered structure.
- It maintained traceability across stories, plans, implementation, guides, and logs.
- It found cross-layer causes from concrete failures, including unified Workout state and stale Auth tokens.
- It automated checks through unit tests, rendered UI tests, pgTAP, CI, Checkstyle, and release tasks.
- It distinguished successful evidence from integration work still blocked on Docker, Supabase, or CI.

## Where the agent created additional work

- Broad visual requests produced usable but not necessarily attractive screens and required several feedback rounds.
- Initial Workout code had extensive Checkstyle violations until style became a repository instruction.
- A detailed early plan amplified assumptions later changed by the Visit/Workout unification.
- Local success sometimes hid cloud differences involving grants, tokens, sequences, seed data, and foreign keys.
- A broad Root Owner change added a new database field before the running backend had it, breaking the Owners list.
  Reverting that data-contract change and using the existing ordering restored the screen, but showed the cost of a
  fix that crossed more layers than the requirement demanded.
- IDE warnings could remain after the source was fixed. The Problems-tab cleanup required checking current code and
  refreshing VS Code's Java project model; a clean Gradle build alone did not clear stale editor diagnostics.
- Long plans and logs sometimes became chronology rather than concise reasoning, creating later editing work.

The agent was most productive when I supplied a named workflow, observable result, exact edge cases, and required
verification level. Human review remained essential for product decisions, visual quality, and live-data safety.

## What I would change next time

1. Prototype the riskiest end-to-end domain assumption before writing a complete plan. A check-in-to-history prototype
   could have revealed earlier that Visit and Workout should be one record.
2. Turn the repeated completion checklist into a small, tested project workflow. I repeatedly asked the agent to find
   the story, inspect affected layers, check authorization and transactions, run Checkstyle and suitable tests, update
   documentation, and write a handoff log. Automating this stable sequence would reduce omissions; changing product
   rules should remain in reviewable stories and architecture documents.
3. Prove that workflow is useful by seeding a style error, missing authorization test, undocumented story change, and
   unverified integration claim. Keep a simple checklist instead if the workflow does not catch these consistently.
4. Replace vague UI advice with a checklist covering primary action, hierarchy, density, minimum size, wrapping,
   scrolling, keyboard use, both themes, and loading, empty, validation, and failure states.
5. Isolate integration data from the start using unique identities, no fixed sequence assumptions, disposable local
   resets, and transactional dependency-aware cleanup.
6. Use short phase checkpoints to review changed assumptions, automated evidence, manual evidence, and uncertainty.
7. For membership, date, and identity rules, state which layer enforces them and test a direct backend request as well
   as the UI path. For JavaFX changes, check asynchronous button state and layout at widths near wrapping thresholds;
   the Owner dashboard's width binding caused a scrollbar and card-wrapping feedback loop that ordinary tests missed.

## What I learned about designing one effective agent

An effective agent needs a hierarchy of context. Stable team decisions belong in stories, architecture, and repository
instructions; task acceptance criteria belong in prompts; manual observations belong in follow-up corrections. This
makes outdated assumptions easier to find.

The agent also needs explicit modes and stopping conditions. Planning should distinguish confirmed and open decisions.
Implementation should finish only after behaviour, tests, style, and documentation. Debugging should explain the cause,
add the right regression test, and disclose unverified integration behaviour. Production work additionally requires
human control over credentials, destructive actions, and cleanup.

More detail is not automatically better. Diagrams clarify actors and state changes, but detailed plans create false
confidence if their assumptions are wrong. Good prompts specify observable outcomes and constraints while allowing the
agent to inspect the repository and propose the smallest consistent change.

Testing must match the failure boundary: service tests protect rules, rendered tests protect stable layout, pgTAP
protects database permissions and atomic functions, integration tests protect authentication and API ordering, and
manual checks protect subjective usability and live operations. No single green suite proves the whole system.

Finally, one agent retains consistency only when discoveries are written back into shared artifacts. `AGENTS.md`,
`ARCHITECTURE.md`, plans, tests, and logs acted as memory between sessions. My role remained to decide product rules,
judge UI quality, challenge stale assumptions, and control production risk. The reliable loop was: agree on scope,
prototype risky assumptions, plan a slice, implement it, verify at the correct layers, inspect reality, and record the
lesson.
