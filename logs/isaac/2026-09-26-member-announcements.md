# Member Announcements Interaction Summary

## Request

Implement M-P1-05 from `docs/Plans/gym-member.md`, stage only the relevant files, suggest a commit message without
committing, and provide Git Bash feature and edge-case checks.

The implementation was then refined through Member and Owner Announcement UI feedback:

- Make Owner withdrawal an explicit visible action on published announcement cards.
- Diagnose and repair the Member Announcements sidebar route when selecting it did not open the page.
- Present complete Member announcement content in the existing themed modal-overlay style.
- Keep the modal title full, bold, and wrapped, while keeping list cards a consistent size.
- Remove nested horizontal and vertical announcement-list scrolling; use the Member shell's normal vertical scrolling.
- Ensure short announcement overlays use a consistent white surface in light mode and one consistent dark surface in
  dark mode.
- Update the Member plan, User Guide, and Developer Guide with the implemented behavior and edge cases.

## Implementation

- Added `MEMBER_ANNOUNCEMENTS`, guarded `AppView` routing, and Member sidebar mappings.
- Added `MemberAnnouncementsView`, which loads `OwnerAnnouncementService.listPublished()` off the JavaFX thread and
  renders only published announcements newest first.
- Added loading, empty, refresh, and safe-error states. A Member may open a card but has no publish, withdraw, or
  withdrawn-history control.
- Added a visible `Withdraw` action to each published Owner announcement card; withdrawal retains Owner history and
  removes the record from the Member list after refresh.
- Fixed the root cause of the broken Member route: `MemberHomeView.navigationItem` omitted the
  `MEMBER_ANNOUNCEMENTS` case, causing an `IllegalArgumentException` while constructing the Member shell.
- Replaced the nested announcement `ListView` with fixed-height `VBox` cards in the outer Member shell's vertical
  scroll container. Card titles and previews are single-line and ellipsized; the detail overlay retains the complete
  title and content.
- Styled the announcement detail as a themed, scrollable overlay. Its title is bold and wraps; short overlays use a
  white light-mode surface and a single `#1f2937` dark-mode surface for the content, unused viewport, and action bar.
- Updated `docs/Plans/gym-member.md`, `docs/UserGuide.md`, and `docs/DeveloperGuide.md` for navigation, withdrawal,
  card sizing/truncation, overlay behavior, scrolling, and theme cases.

## Tests and Verification

- Extended `OwnerAnnouncementServiceTest` to explicitly cover published-only newest-first results and withdrawn
  visibility.
- Added `MemberAnnouncementsViewTest` coverage for multiline preview normalization, title truncation, and both Member
  Announcements screen-to-label mappings.
- Repeatedly ran the complete Java checks after refinements:

```bash
set GRADLE_USER_HOME=C:\tmp\gymflow-gradle
gradlew.bat checkstyleMain checkstyleTest test --no-daemon
```

- Checkstyle and the full test suite passed after the final light/dark overlay styling changes.
- Gradle caches were kept outside the repository at `C:\tmp\gymflow-gradle`.

## Handoff

- All implementation, stylesheet, documentation, and test files for M-P1-05 were staged.
- No commit was created.
- Suggested commit message: `feat(member): add published announcements view`.
