# Member Body-Mass Tracking Interaction Summary

## Request

Implement M-P1-04 from `docs/Plans/gym-member.md`, stage only the relevant files, suggest a commit message without
committing, and provide Git Bash checks for the feature and edge cases.

The implementation was then refined through several Member Measurements UI requests:

- Add a line graph and an inline latest-weight editor.
- Use a desktop interpretation of the supplied Measurements reference screen, with light and dark theme support.
- Reduce clutter by prioritizing a daily update workflow and hiding date correction until requested.
- Open the calendar immediately when `Change date` is selected and display the date of the latest measurement.
- Support longer-term graph periods and a custom inclusive date range.
- Keep custom range controls compact, place them beside the `Custom` toggle, and allow the same toggle to hide them.

## Implementation

- Added `BodyMetric`, `BodyMetricStore`, and `BodyMetricService`.
- Added schema version 8 with `body_metrics`: Member ownership, positive integer gram values, timestamps, and a unique
  Member/date constraint.
- Enforced active-Member authorization, positive values, three-decimal kilogram precision, and no future dates.
- Added CRUD UI at `Workouts` → `Body mass`, including edit/delete history and confirmation before deletion.
- Added latest-mass display, prior-reading change feedback, a graph, and date-range filtering.
- Added graph presets for one month, three months (default), six months, one year, and all recorded history.
- Added a `Custom` range toggle with compact From/To date pickers and validated inclusive filtering.
- Styled the Measurements workflow, chart, controls, and history for existing light and dark themes.
- Updated `docs/UserStories.md`, `docs/UserGuide.md`, and `docs/DeveloperGuide.md` to describe the implemented feature.

## Tests and Verification

- Added service tests for valid persistence, ordering, updates, duplicate dates, future dates, non-positive values, and
  excessive decimal precision.
- Added UI-helper tests for prior-reading change text and inclusive graph-range filtering.
- Ran `gradlew.bat check` successfully after the UI/workflow update.
- Ran focused checks successfully after the subsequent graph-control refinements:
  `gradlew.bat checkstyleMain test --tests com.gymflow.ui.MemberBodyMetricsViewTest`.
- Kept Gradle caches outside the repository under the system temporary directory.

## Handoff

- All feature, UI, documentation, and test files were staged.
- No commit was created.
- Suggested commit message: `feat(member): add body mass trend chart and inline editor`.
