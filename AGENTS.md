# Development hygiene

- Keep Gradle caches outside the repository. Do not create a project-local Gradle user home or cache directory unless it is required by the execution environment; if one is required, remove it before handoff and never stage it.
