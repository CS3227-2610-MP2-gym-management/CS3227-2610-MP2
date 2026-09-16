# Card-list interface

## Goal

Replace dense record tables with responsive cards across GymFlow while preserving existing behaviour, role boundaries,
searches, filters, and actions.

## Agent interaction summary

- Chose JavaFX `ListView` with custom cells so long lists remain virtualized and vertically scrollable.
- Added a small shared card-list helper for consistent empty states, wrapping, accessibility, and theme styling.
- Converted Owner overview, Members, Memberships, Finances, Visits, Announcements, Member histories, and the Member
  preview. Member and Announcement cards open details; Visit and Membership cards expose their existing actions inline.
- Kept the model, database, stores, and services unchanged because this is a presentation-only improvement.
- Updated the guides and contribution/reflection records to describe the card interactions.

## Verification

The Gradle checks and all platform release JAR tasks are run before completion. Manual review should cover keyboard
activation, long wrapped values, scrolling at the minimum window size, and both light and dark themes.
