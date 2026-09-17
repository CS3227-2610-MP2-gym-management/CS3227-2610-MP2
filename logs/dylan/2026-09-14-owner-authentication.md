# Owner Setup, Login, and Reset

## Goal

Add the first functional Owner authentication slice without implementing Member authentication or domain features.

## Prompt and interaction summary

- Planned first-run Owner setup in the existing Login screen rather than creating a registration screen.
- Selected one Owner per installation and a local SQLite database at `data/gymflow.db`.
- Implemented salted PBKDF2-HMAC-SHA256 passwords, normalized emails, login, logout, and in-memory session handling.
- Added a destructive reset guarded by the current password and exact `RESET` confirmation.
- Added platform-aware SQLite packaging and README instructions.
- Explained local-database ownership, password salting, PBKDF2, and why the production slice was larger than a
  prototype login.
- Ran Ponytail Full and removed test-only scaffolding in a follow-up commit.

## Agent skills and tools

- Superpowers brainstorming, writing-plans, executing-plans, TDD, debugging, and verification
- Ponytail Full for minimality and YAGNI review
- JUnit integration tests using temporary SQLite databases
- Gradle, Checkstyle, JAR-content verification, and Git

## Important decisions and corrections

- Passwords use 600,000 PBKDF2 iterations, independent random salts, and constant-time comparison.
- Database and hashing work run outside the JavaFX application thread.
- Reset recreates the centralized schema in one transaction and creates no backup.
- Owner preview was removed; temporary Member preview was retained.
- JavaFX field values were captured on the UI thread before background tasks used them.
- The `.gitignore` rule changed from `data/` to `/data/` so the runtime database stayed ignored without hiding the
  `com.gymflow.data` source package.
- `AccountStore.setActive(...)` and the reflection-only `AppViewTest` were removed after review.

## Outcome and evidence

- Branch: `feature/potatoad88-owner-login`
- Commits: `871ea27`, `b6c168a`
- Pull request: #1, merged as `2f53932`
- Final reported verification: 11 automated tests, Checkstyle, and four release-JAR builds passed.
