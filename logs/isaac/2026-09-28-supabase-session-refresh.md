# Supabase Session Refresh Regression

**Date:** 28 September 2026  
**Status:** Fixed; Supabase integration verification pending CI or a running local backend

## Scope

Investigated the failing `SupabaseFeatureIntegrationTest` reported by GitHub Actions, fixed the stale authentication
session used after a Member changes their email, and documented when and how future Supabase Auth changes must force
a session-token refresh.

## Reported failure

The Supabase integration job completed compilation and ran 117 tests, but
`SupabaseFeatureIntegrationTest.ownerAndMemberFeaturesShareTheLocalCloudBackend()` failed with an
`IllegalStateException` at the Member self-service contact update. The failure occurred after an Owner changed the
Member's login email and password and the Member authenticated with those new credentials.

## Investigation

The failing line called `MemberAccountService.updateContact`. Its cloud path performed these operations:

1. Call the protected `manage-member` Edge Function.
2. Change the current Auth user's email through `auth.admin.updateUserById`.
3. Immediately reload the Member profile with the access token issued before the Auth change.
4. Reload the application Account without necessarily exchanging the old token.

Supabase documents that clients should refresh their session after an administrator updates their Auth user. The
existing `refreshAccount()` implementation called `requireAccessToken()`, which only refreshed a token near expiry.
It therefore did not guarantee that the token contained the updated identity claims. The profile read also happened
before that ineffective refresh attempt.

The local Supabase integration environment could not be reproduced because the Supabase CLI was unavailable and the
Docker engine was inaccessible. The ordinary Java tests and source inspection were used to isolate the ordering and
token-refresh defect.

## Fix

`SupabaseAuthenticationService.refreshAccount()` now unconditionally exchanges the stored refresh token before it
reloads the application Account. `SupabaseMemberAccountStore.updateContact()` now performs only the protected update;
it no longer performs an immediate profile read. `MemberAccountService.updateContact()` uses the required order:

1. Complete the Auth-changing Edge Function request.
2. Force the session refresh and reload the Account.
3. Fetch the updated Member profile with the new access token.

This prevents the first post-update Data API request from using stale authentication claims.

## Developer documentation

Added an idiot-proof session-refresh section to `docs/DeveloperGuide.md`. It explains:

- when an Auth change requires a forced refresh;
- why ordinary PostgreSQL/Data API writes do not require one;
- the exact update, refresh, then reload ordering;
- why `requireAccessToken()` is not a substitute;
- correct and incorrect Java examples;
- a troubleshooting checklist for the integration-test failure; and
- the requirement never to print passwords, access tokens, refresh tokens, or service-role keys.

## Verification

- `gradlew.bat --no-daemon checkstyleMain test` passed.
- All locally enabled Java tests passed.
- `git diff --check` passed.
- The environment-gated Supabase integration flow remains to be confirmed by CI or with the local Supabase stack and
  Edge Function runtime running.
