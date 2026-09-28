# User Guide Walkthrough and Screenshot Guidance

**Date:** 29 September 2026  
**Status:** Documentation updated; screenshots remain to be inserted

## Scope

Reworked the README and User Guide for someone cloning GymFlow and testing it for the first time. The conversation
covered local and production setup, account order and credentials, the current first-run behavior, screenshot
placement, and how to embed image assets in Markdown.

## Conversation and Changes

1. The requested guide needed a logical sequence from setup through Owner and Member features. The README now points
   to an ordered local walkthrough; the User Guide gives the setup commands, seeded account credentials, co-owner and
   Member creation steps, feature checks, and role switches.
2. The local seed was checked against `supabase/seed.sql`. It provides one Owner and two Members. The guide also gives
   concrete example credentials for the additional co-owner and Member created during the walkthrough.
3. The README and User Guide include the supplied production test co-owner credential and direct readers to contact
   Telegram `@ITZXITZX` for the Supabase publishable key if needed. Production instructions limit ordinary testing
   to sign-in and Owner Home; disposable feature testing runs against local Supabase.
4. An initial `Essential screenshots` section incorrectly addressed the document author. After feedback, it was
   removed from the User Guide, and the README reference to screenshot planning was removed. Screenshot placement
   guidance was given directly in the conversation instead.
5. A follow-up question recalled a first-run Owner creation screen. Startup code, the local reset script, and the
   Supabase seed were checked. The current app uses Supabase and opens on sign-in; `npm run supabase:reset` seeds the
   first local Owner. It does not need or create `data/gymflow.db` for normal operation. Both guides now state this
   concisely and explain that additional Owners are created from the `Owners` page after sign-in.
6. For manual screenshots, the guidance was to place assets under `docs/images/` and embed them next to the relevant
   step with a relative Markdown path, for example `![GymFlow local sign-in screen](images/login-local.png)`.
   Screenshots should omit visible passwords, keys, and personal data. No screenshot files were created in this work.
7. Links from the README, Developer Guide, and Production Deployment guide were updated to the renamed local
   walkthrough section.

## Verification

- Read the current startup, local reset, and seed code before documenting first-run behavior.
- Ran `git diff --check`; no whitespace errors were reported. No Java code changed, so Checkstyle was not required.
