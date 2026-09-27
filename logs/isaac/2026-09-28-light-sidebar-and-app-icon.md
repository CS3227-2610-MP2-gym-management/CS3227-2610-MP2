# Light Sidebar and GymFlow App Icon

## Request

Make the left navigation panel follow light mode. Then reuse the blue `GF` mark from the login screen as the
application icon, including the main window and all dialog windows. Document the conversation and suggest a commit
message.

## Implementation

- Changed the sidebar's default background, border, brand, role, navigation, and logout colors to a light palette.
  Kept the original sidebar colors under the `.dark` theme selector so switching themes updates the whole panel.
- Created `src/main/resources/images/gymflow-icon.png`, a blue rounded-square icon with white `GF` lettering based
  on the login mark.
- Added `AppIcon` to share the packaged image. The login screen now displays that image, and the primary stage uses
  it as its window icon.
- Applied the icon to dialogs styled through `UiComponents.styleDialog` and to the remaining plain alerts in the
  Member home and body metrics views.
- Removed the unused CSS rule for the old text-based login mark.

## Validation

- Visually inspected the generated PNG and ran `git diff --check`.
- `gradlew.bat checkstyleMain -x compileJava` passed with `GRADLE_USER_HOME` under the system temporary directory.
- The normal Gradle compile and test run could not complete because dependency JARs in that temporary Gradle cache
  were unreadable. No project-local Gradle cache was created.

## Suggested Commit

`Fix light sidebar and add GymFlow window icon`
