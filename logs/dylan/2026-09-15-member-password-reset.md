# Owner-Managed Member Password Interaction Summary

> Verification status: Pending Dylan review

## Goal

Allow the Owner to choose each new Member's initial password and replace it later without implementing Member-facing
screens.

## Decisions

- Preserved the existing initial-password and confirmation fields in Member onboarding.
- Added password reset to the Owner-visible Member profile.
- Required a 12–128 character replacement password and matching confirmation.
- Used a fresh PBKDF2 salt and hash for every reset; plain-text passwords are neither logged nor stored.
- Relied on the authenticated Owner session plus a store-level active-Owner check instead of requesting the Owner's
  password again.
- Kept stronger password-and-`RESET` authorization for full GymFlow reset because that operation deletes all data.
- Left forced password changes, recovery tokens, password history, and Member login routing outside this Owner slice.

## Agentic SE use

- Superpowers brainstorming separated ordinary Owner administration from destructive reset authorization.
- Test-driven development covered credential replacement, validation, authorization, inactive Members, and preservation
  of other Member records.
- Ponytail Full kept the implementation within the existing service, store, dialog, and background-task patterns.

## Verification

- Automated and manual verification results must be added after Dylan reviews this branch.
