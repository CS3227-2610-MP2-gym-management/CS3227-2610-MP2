# Member Renewal Guidance and Rendered UI Tests Interaction Summary

## Goal

Implement M-P0-08 from `docs/Plans/gym-member.md`: provide clear, informational in-person Membership renewal guidance
to authenticated Members with no active or upcoming Membership. Make the guidance prominent at login, correct related
Member-page layout issues, add rendered JavaFX regression coverage, update the relevant documentation, validate from
Git Bash, and stage changes without creating a commit.

## Delivered behaviour

- `MemberAccountService` derives one immutable `MembershipNotice` from a Member's ordered Membership history and the
  injected clock. `ACTIVE` takes precedence over `UPCOMING`; otherwise the state is `RENEWAL_NEEDED`.
- Member Home displays a prominent, accessible amber `Membership renewal needed` card before Gym Visit controls only
  for `RENEWAL_NEEDED`. It explains that check-in is unavailable and directs the Member to visit the gym in person.
- My Membership consumes the same shared notice decision and presents in-person guidance when needed. It no longer
  nests a rounded outer card around the rounded Membership-history cards.
- Guidance remains read-only: it creates no Membership, Payment, purchase request, or online-payment flow.
- Member Home detail labels now shrink to the available card width and wrap long text instead of rendering ellipses.
- The shared Member shell now wraps its content in a fit-to-width vertical `ScrollPane`, so Member Home, My Membership,
  Gym Visits, and later Member pages using that shell can scroll when their content exceeds the window.

## Rendered JavaFX testing

- Added a separate `renderedUiTest` Gradle source set and task. It is not part of the ordinary `check` task because it
  creates real JavaFX desktop windows and is unsuitable for headless cross-platform CI by default.
- Added a JavaFX toolkit test helper and rendered tests for the shared Member scroll container and multi-line detail
  text wrapping.
- Added a stable Member-scroll-pane ID for rendered tests and future UI interaction tests.

Run the checks from Git Bash in the repository root:

```bash
./gradlew clean check --console=plain
./gradlew renderedUiTest --rerun-tasks --console=plain
```

The normal Gradle test suite and the force-run rendered JavaFX task passed locally. The JDK emitted its expected
native-access warning while loading SQLite and JavaFX native libraries; it did not affect the successful results.

## Documentation updated

- `docs/UserStories.md` records the prominent Home notice and My Membership guidance for M-P0-08.
- `docs/UserGuide.md` explains the Member-facing renewal notice and the rendered UI test command.
- `docs/DeveloperGuide.md` documents the shared `MembershipNotice` decision, the dedicated rendered-test task, and its
desktop-display requirement.
- `docs/ProjectDecisions.md` records the approved renewal-notice scope, accessibility requirement, precedence, and
out-of-scope expiring-soon behavior.
- `ARCHITECTURE.md` records `MembershipNotice` as a derived read model and defines the boundary between rendered tests
and manual JavaFX verification.

## Important decisions and follow-up

- Do not add an online payment or Member self-service renewal path without a new approved story.
- Do not add an expiring-soon warning until the threshold, wording, and acceptance criteria are agreed.
- Rendered tests cover stable scene/layout regressions. Keyboard focus, dialogs, full interaction flows, theme contrast,
  native launch, and accessibility still require manual verification.
- All changes were staged as requested. No commit was created by the agent.
