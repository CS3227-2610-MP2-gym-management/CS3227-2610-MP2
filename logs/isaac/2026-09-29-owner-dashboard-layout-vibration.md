# Owner Dashboard Layout Vibration

**Date:** 29 September 2026  
**Status:** Fixed and verified

## Scope

Investigated a report that the content half of both the production and local Owner overview rapidly vibrated from
side to side. The behavior depended on the window width, sometimes affecting the right half and sometimes
disappearing after a resize. Determined whether the view was repeatedly reloading, traced the regression to the
preceding Owner overview refactor, implemented the fix, and explained why the previous layout logic was unstable.

## Findings

- The application was not constantly reloading and the issue was unrelated to local or production data access.
- Commit `703577f` (`refactor: simplify owner overview`) bound every statistic card's preferred width to the width of
  its containing `FlowPane`.
- Near a column-wrapping boundary, pixel rounding or a vertical scrollbar changed the available viewport width.
- The width change recalculated the cards, changed their wrapping and content height, toggled the scrollbar, and
  started the same calculation again. JavaFX consequently oscillated between two layouts.
- The sidebar remained stable because it had a fixed width; only the scrollable Owner content participated in the
  feedback loop.

## Changes

- Removed the `prefWidthProperty()` binding from the Owner overview statistic cards.
- Removed the now-unused `javafx.beans.binding.Bindings` import.
- Retained the cards' existing stable preferred width from `UiComponents.statCard()` and their `220`-pixel preferred
  height.
- Left the `FlowPane` responsible for naturally wrapping fixed-width cards as the window narrows.

The resulting layout dependency is one-directional: the available width determines card placement. Card widths no
longer feed back into the parent width, wrapping, page height, and scrollbar state.

## Verification

- Ran `gradlew.bat check` with the Gradle user home outside the repository.
- All unit tests passed.
- `checkstyleMain`, `checkstyleTest`, and `checkstyleRenderedUiTest` passed.
- The full Gradle `check` task completed successfully.

