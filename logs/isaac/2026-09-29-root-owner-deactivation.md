# Root Owner Deactivation and Owner Labels

**Date:** 29 September 2026  
**Status:** Fixed and verified

## Scope

Updated the Owners tab so the original Root Owner cannot be deactivated by a co-owner. The conversation also covered
an unsuccessful database-backed implementation, the resulting Owner-list loading failure, its rollback, and a label
correction so co-owners are no longer described as Gym administrators.

## Conversation and Changes

1. The initial report showed that a signed-in co-owner could deactivate the original
   `ignite.illume@gmail.com` account. The requested behavior was to keep the original account visible while disabling
   its `Deactivate` button.
2. The first implementation introduced an `is_gym_administrator` field into the client account query and model, plus
   related migration, seed, and database-test changes. This was broader than the requested UI change and caused the
   Owners tab to display `Unable to access Owner accounts` when the running backend did not yet expose that column.
3. Those account-model, query, seed, and database-test changes were reverted. The Owners tab returned to its original
   data contract and continued loading the same account records in both local development and production builds.
4. The final implementation uses the existing `created_at.asc` Owner ordering: the first account is treated as the
   Root Owner. Its activation button is always disabled. The existing rule that disables the current signed-in
   Owner's button remains unchanged, while other co-owners can still be activated or deactivated.
5. Testing the final behavior from the Root Owner account revealed stale display logic: every non-current Owner was
   labelled `Gym administrator`, causing `test@example.com` to receive the wrong label.
6. Owner labels were updated to the following four states:
   - `Root Owner`
   - `Current signed-in Root Owner`
   - `Owner`
   - `Current signed-in Owner`
7. The Owners-page subtitle was changed from `Provision and manage gym administrators` to
   `Provision and manage Owners`, removing the Gym administrator terminology from the screen.

## Tests and Verification

- Added focused tests for Root Owner protection, signed-in Owner protection, ordinary co-owner management, and all
  four display labels.
- Ran `gradlew.bat check` with the Gradle user home outside the repository.
- Java tests and all Checkstyle tasks passed.

## Final Behaviour

- The original `ignite.illume@gmail.com` account is consistently identified as the Root Owner regardless of which
  Owner is signed in.
- The Root Owner remains visible and its `Deactivate` button is disabled.
- A signed-in co-owner cannot deactivate their own account but can manage other co-owners.
- No account is labelled Gym administrator on the Owners tab.
