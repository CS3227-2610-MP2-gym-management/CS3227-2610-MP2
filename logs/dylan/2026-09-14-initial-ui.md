# Initial GymFlow UI

## Goal

Create the initial JavaFX project and three static screens: Login, Owner Home, and Member Home.

## Prompt and interaction summary

- Reviewed the assignment constraints, gym-management concept, role split, user stories, and proposed entities.
- Reviewed Google Stitch output supplied as local files and used it as visual direction for JavaFX.
- Planned a presentation-only initial commit with no authentication, persistence, or operational data.
- Implemented programmatic JavaFX views, shared CSS, single-scene navigation, Gradle, Checkstyle, JUnit, CI, CodeQL,
  and four platform JAR tasks.
- Investigated JavaFX launch failures caused by the automation environment and compared behaviour with MP1.
- Changed the project configuration to match the working MP1 setup where appropriate.

## Agent skills and tools

- Superpowers brainstorming and planning for scope and UI decisions
- Ponytail review for minimality
- Gradle tests, Checkstyle, release-JAR inspection, and manual user visual verification

## Important decisions and corrections

- Used three screens and no separate Member registration screen.
- Kept preview navigation temporary and excluded classes, lockers, RFID, and server indicators.
- Removed a duplicate Login-screen construction from startup.
- Added VS Code Java project settings to resolve IDE-only JavaFX errors.
- Removed the repository licence after deciding not to grant an open-source licence for the school project.

## Outcome and evidence

- Commits: `adfd335`, `66f0b69`
- Pull-request evidence: initial work was committed directly before the later feature-branch workflow.
- Verification reported at the time: Gradle checks, release tasks, and Dylan's visual launch check passed.
