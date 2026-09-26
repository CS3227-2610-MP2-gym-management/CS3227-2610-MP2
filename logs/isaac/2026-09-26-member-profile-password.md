# Member Profile and Password Interaction Summary

## Request

Implement M-P1-06 and M-P1-07 from `docs/Plans/gym-member.md`, stage only the relevant files, suggest a commit
message without committing, and provide Git Bash feature and edge-case checks.

After implementation, document this entire interaction under `logs/isaac` in the style used by the existing Isaac
interaction summaries.

## Implementation

- Added `AccountValidation` to centralize email normalization, Singapore phone normalization, and the 12-128
  character password policy. Existing authentication and Owner Member workflows now use the shared rules.
- Added Member-owned contact updates in `MemberAccountStore`. The operation updates only email and phone, validates
  that the target is an active Member, and rolls back both changes when a duplicate email or storage failure occurs.
- Added account credential lookup and active-Member password replacement operations in `AccountStore`.
- Extended `MemberAccountService` with self-service `updateContact` and `changePassword` commands. Password change
  verifies the current credential before creating a fresh hash and clears both supplied password arrays on every path.
- Replaced the existing Member Profile placeholder with `MemberProfileView`, providing editable email and phone
  fields plus current/new/confirmation password fields. The confirmation mismatch is rejected in the UI before the
  service call; successful updates keep the Member session active.
- Marked M-P1-06 and M-P1-07 as implemented in `docs/UserStories.md`.

## Tests and Verification

- Added Member-account service tests for normalized contact updates, unchanged immutable profile fields, duplicate
  email rejection, verified password replacement, wrong-current-password safety, and cleared password buffers.
- Ran `gradlew.bat test checkstyleMain` successfully. The initial Gradle invocation could not create its cache under
  `C:\.gradle`; rerunning with `GRADLE_USER_HOME` set to `C:\Users\isaac\.gradle` kept the cache outside the project
  and completed successfully.
- Provided Git Bash steps to run automated checks, launch the application, verify normal profile/password flows, and
  exercise invalid contact input, duplicate email, wrong current password, invalid password length, confirmation
  mismatch, and old-versus-new login behavior.

## Staging and Handoff

- Staged only the feature implementation, test, and User Story files. No commit was created.
- Suggested commit message: `feat(member): add self-service profile and password management`.
