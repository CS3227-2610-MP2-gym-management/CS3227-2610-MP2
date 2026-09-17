# Owner Announcement Management Interaction Summary

## Request and decisions

Dylan requested gym-wide Owner announcements with retained withdrawal history. Announcements may be published or
withdrawn, but not edited or permanently deleted. Read/unread tracking and Member-facing implementation are outside
this branch.

## Agent work

- Added the version-5 Announcement schema and reset coverage.
- Added active-Owner-authorized publication and withdrawal with trimmed required fields.
- Added Published and Withdrawn tabs, full detail views, inline publish validation, and withdrawal confirmation.
- Exposed `listPublished()` as the shared contract for the future Member interface.
- Reused existing background-task, dialog, navigation, table, scrolling, and clipping-safe UI patterns.
- Relocated the guarded full-data reset from the Overview testing card to every Owner sidebar.

## Verification required

Dylan should test publication, validation retention, full details, withdrawal, history persistence after restart,
minimum-window layout, scrolling, and the release JAR.
