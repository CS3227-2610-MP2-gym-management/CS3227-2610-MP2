# Owner Member Management

## Goal

Implement Owner-side Member onboarding, search, and profile editing without implementing Member-facing workflows.

## Prompt and interaction summary

- Reconfirmed that Dylan owns Owner features while the teammate owns Member login, screens, and entry/exit forms.
- Split Owner P0 into three branches and selected Member management as the first slice.
- Defined onboarding as one transaction containing the Member account, profile, initial Membership, and Payment.
- Selected Owner-entered initial passwords, generated member numbers, and case-insensitive name/email search.
- Used failing integration tests before implementing the SQLite schema, service, store, and Owner Members page.
- Added required documentation updates alongside the feature.
- Used Dylan's manual review to identify inconsistent native dialogs, premature closing on validation failure, and a
  misleading Overview action.
- Revised the dialogs to share GymFlow styling, preserve input on failure, and open Member creation directly from
  Owner Overview.
- Tightened the Members page search so Enter triggers the query, clearing the field restores all rows, and matching is
  limited to name or email.
- Replaced the selected-row edit dialog with a full-page Member profile. Row selection opens profile details, payment
  history is shown read-only, and editing happens in-page with Cancel and Save Changes actions.

## Agent skills and tools

- Superpowers brainstorming, executing-plans, TDD, and verification
- Ponytail Full for minimal production APIs and avoidance of test-only methods
- JUnit with a real temporary SQLite database
- Gradle, Checkstyle, release-JAR verification, and Git

## Important decisions and corrections

- No Member-facing JavaFX view or Member workflow was added.
- Initial password arrays are cleared after onboarding.
- Member number, account, profile, Membership, and Payment are created atomically.
- Payment history is displayed through the existing Membership-to-Payment relationship, without changing the database
  schema.
- A duplicate email is translated from a SQLite constraint into a user-facing validation failure.
- Tests query their temporary database directly instead of adding production count methods used only by tests.
- Only Overview and Members are active in the Owner sidebar; later pages remain disabled for their own branches.
- Member phone numbers are intentionally limited to Singapore `+65` numbers, and supplied birth dates require a
  minimum age of 12.

## Outcome and evidence

- Branch: `feature/potatoad88-member-management`
- Commit: this feature commit; pull request pending
- Verification: `./gradlew clean check releaseJars` passed on 14 September 2026
