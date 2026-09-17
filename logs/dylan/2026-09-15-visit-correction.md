# Owner Visit Correction Interaction Summary

## Goal

Allow the Owner to correct inaccurate or incomplete Visit timestamps without taking over Member entry and exit.

## Decisions

- Allowed entry changes and adding, changing, or clearing an exit time.
- Required a non-blank reason and recorded the correction time and Owner account ID.
- Retained only the latest correction metadata instead of adding a full audit-history subsystem.
- Rebuilt version-2 Visit tables transactionally so database constraints apply after migration.
- Reused the one-open-Visit index to prevent corrections from creating two ongoing Visits for one Member.
- Did not re-check historical Membership eligibility during correction.

## Agentic SE use

- Superpowers planning established the Owner/Member ownership boundary and correction rules.
- Test-driven development covered migration, authorization, invalid timestamps, reopening, and preservation.
- Ponytail Full kept correction inside the existing Visit record, store, service, and page.

## Verification

- Automated correction and migration tests passed before this commit.
- Dylan should verify the correction dialog in the macOS JAR before treating this summary as final evidence.
