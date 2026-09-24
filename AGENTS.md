# Development hygiene

- Keep Gradle caches outside the repository. Do not create a project-local Gradle user home or cache directory unless it is required by the execution environment; if one is required, remove it before handoff and never stage it.
- Treat `config/checkstyle/checkstyle.xml` as mandatory for all Java changes. Before handoff, resolve every Checkstyle violation and run `gradlew.bat checkstyleMain` (or `gradlew.bat check`) when the build environment is available.
