# Supabase RPC Signature Fix

**Date:** 28 September 2026  
**Status:** Fixed and verified locally

## Scope

Investigated the failing Supabase integration test, traced it to an omitted Edge Function RPC argument, applied the
smallest safe fix, and reproduced the complete Supabase integration workflow locally until all tests passed.

## Reported failure

GitHub Actions completed 117 Java tests with one failure:
`SupabaseFeatureIntegrationTest.ownerAndMemberFeaturesShareTheLocalCloudBackend()`. The Member self-service contact
update returned HTTP 400 because PostgREST could not find
`public.update_member_records(p_email, p_full_name, p_member_account_id, p_phone_number, p_update_identity)` in its
schema cache.

## Diagnosis

- The database migration defines `update_member_records` with six required parameters, including
  `p_date_of_birth`.
- A Member self-update does not supply a date of birth, so the Edge Function assigned JavaScript `undefined` to that
  RPC field.
- Supabase JS omitted the undefined property from the request. PostgREST consequently tried to resolve a five-argument
  function that did not exist.
- The schema-cache wording was misleading: the function and migration existed, but the effective RPC signature did
  not match.
- Owner updates had not exposed the issue because they supplied a date of birth.

## Fix

Changed the `manage-member` Edge Function to send an explicit SQL null when the optional date is absent:

```typescript
p_date_of_birth: payload.date_of_birth ?? null,
```

This is safe for Member self-updates because `p_update_identity` is false, and the SQL function preserves the existing
date of birth in that case. No migration or schema-cache reload is required.

## Local verification

- Reset the local Supabase database and reapplied every migration and the seed data successfully.
- Ran all Supabase database tests: 165 passed.
- Forced the Java suite to rerun with `GYMFLOW_LOCAL_INTEGRATION=true`: 117 passed, 0 failed, 0 skipped.
- Confirmed the previously failing Supabase feature integration test passed.
- Ran `gradlew.bat --no-daemon checkstyleMain`: passed.
- Ran `git diff --check`: passed.
- Kept the Gradle user home in the system temporary directory; no project-local Gradle cache was created.

## Suggested Commit Message

`Fix member self-update Supabase RPC arguments`
