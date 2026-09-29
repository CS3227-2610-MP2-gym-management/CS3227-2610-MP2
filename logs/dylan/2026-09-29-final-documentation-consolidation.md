# Final documentation and product website consolidation

**Date:** 29 September 2026
**Status:** Completed

## Request

Dylan requested a submission-facing documentation pass: combine both individual reflections, audit the User Guide as
a fresh-clone walkthrough, refresh the GitHub Pages product site, create an evidence-backed contribution record for
Isaac, and correct stale contribution claims. No application behaviour, schema, secret, or production record was to
change.

## Work completed

- Replaced the incomplete team reflection stub with attributed Dylan and Isaac sections plus a team synthesis. The
  individual reflections remain source records, and the canonical document states truthfully that customization used
  prompts, plans, repository instructions, and installed workflows rather than a custom GymFlow skill.
- Added `docs/Contributions/Isaac.md` using commits and merged PRs #11–#16 and #18. Corrected Dylan's Owner Overview
  description and replaced the CSV placeholder with commit `7bd4afc` and PR #10.
- Reworked the User Guide opening into macOS/Linux and Windows fresh-clone instructions covering Java 25 architecture,
  Node/npm, Docker, local Supabase, the protected Edge Function, `runLocal`, seeded credentials, and shutdown. It now
  explains that release JARs require a configured backend and that there is no public signup or first-launch Owner
  creation.
- Clarified across the README, User Guide, Developer Guide, and product website that Docker and Node/npm are required
  for disposable local review, while a hosted-production JAR needs Java 25 and the maintainer-supplied production
  Supabase environment variables instead.
- Updated the static product website to describe the role-based JavaFX/Supabase application, current Owner and Member
  capabilities, local-review setup, backend and desktop verification, CI, diagnostic logging, and uptime monitoring.

## Evidence and review boundary

Claims were checked against package scripts, current source and guide labels, merged git history, the Pages workflow
that uploads `site/`, and the uptime workflow URL. Script tests and the complete Gradle check passed; HTML, CSS,
Markdown links, anchors, screenshots, commit references, and whitespace were also checked. Dylan should visually
review the website at desktop and mobile widths and review the combined reflection and walkthrough; Isaac should
review his attributed reflection and contribution record before submission.

## Agent workflow

The agent used the approved consolidation plan, repository inspection, commit-history evidence, and a minimal
dependency-free website edit. No custom GymFlow skill was created or claimed.
