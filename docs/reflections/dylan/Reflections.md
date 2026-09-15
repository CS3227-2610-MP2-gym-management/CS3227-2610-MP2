# Dylan's Agentic SE Working Reflection

> Verification status: Pending Dylan review

This working document captures evidence during development. It should be edited into Dylan's own final voice after the
corresponding logs and commits have been checked.

## Agent customization and skill selection

The agent was instructed to use Ponytail for precise, minimal code changes and Superpowers when planning or designing
features. Skills were selected according to the task: brainstorming and writing-plans for decisions, TDD for behaviour
and security work, systematic debugging for failures, and verification before completion claims.

A project-specific GymFlow skill has not yet been committed to this repository. Defining and testing that skill remains
an explicit assignment task; the current evidence demonstrates configuration of an existing agent skill set rather
than completion of a new custom skill.

## Example 1: Brainstorming and planning

Before implementing authentication, the agent helped decide that GymFlow would support one Owner per local
installation, use the existing Login screen for first-run setup, and require both the Owner password and exact `RESET`
text for a factory reset. This reduced ambiguity before schema and UI work began.

The same process identified the boundary between Dylan's Owner work and the teammate's Member work. Shared entities,
storage, and validity queries may be implemented by Dylan, while Member-facing screens and entry/exit workflows remain
outside Dylan's scope.

The limitation was that detailed production requirements expanded a seemingly small login feature into password
security, persistence, packaging, reset safety, and tests. Future plans should label this hidden scope earlier and
split large work into independently reviewable branches.

## Example 2: Test-driven development

Password hashing, SQLite persistence, authentication, and reset behaviour were developed through failing tests followed
by minimal implementations. Tests covered randomized salts, exact Unicode and whitespace passwords, validation,
Owner uniqueness, database reopening, inactive-account rejection, reset authorization, and transactional rollback.

This improved confidence in security and destructive reset behaviour. It also made the implementation larger than a
prototype login, but that additional work was justified because these failures would otherwise risk credentials or
application data.

The agent still required correction during UI integration: JavaFX control values initially risked being read from a
background thread. Reviewing the complete data flow caught the issue and moved control reads back to the JavaFX thread
while leaving hashing and SQLite work in background tasks.

## Example 3: Ponytail review

Ponytail Full was used to check whether the authentication commit was concise. It identified
`AccountStore.setActive(...)` as production code used only to arrange a test and identified a reflection-only
`AppViewTest` that checked API shape rather than user-visible behaviour.

The cleanup removed both while preserving inactive-account testing through the temporary SQLite database. This was a
useful example of the agent creating extra work first and then using a focused review skill to remove it. A better
initial instruction would explicitly prohibit production methods whose only caller is a test.

The Member-management slice applied that lesson immediately: its integration test inspected Membership and Payment
rows directly rather than adding count methods used only by tests.

Manual UI review found that JavaFX dialogs did not inherit the application stylesheet and closed before asynchronous
validation completed. The agent traced this to the dialog submission lifecycle, then reused one stylesheet hook and
consumed submit events until success. This was more effective than adding a custom dialog framework and reinforced the
need to include failure-state interaction checks, not only happy-path screenshots, in future agent instructions.

The Membership-management slice used the same skills to define a narrow cross-role contract without taking over the
Member UI. The agent exposed a date-based Membership validity query for the teammate while keeping activation and
Payment controls exclusively in the Owner screens. A failing legacy-database test also drove an idempotent timestamp
migration, avoiding the easier but destructive option of requiring another application reset.

## Lessons and future improvements

- Agent instructions work best when they define ownership boundaries and explicit exclusions.
- Security and destructive operations require stronger verification than ordinary presentation changes.
- A minimal-code skill is most useful after the whole flow is understood; applying it too early can hide requirements.
- Future GymFlow skills should require user-story traceability, role-boundary checks, TDD for business rules, a
  production-only caller check, and documentation/log updates before completion.
