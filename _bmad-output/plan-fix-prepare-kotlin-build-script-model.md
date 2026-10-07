---
title: 'Fix chain-listener Kotlin DSL model task lookup'
type: 'bugfix'
ticket: ''
created: '2026-10-06'
status: 'built'
baseline_revision: 'ca1912449993a6b498fd201657b6ada634d08283'
route: 'oneshot'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: ['quick']
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent - do not modify unless human renegotiates">

## Intent

**Problem:** IntelliJ/Gradle requests `prepareKotlinBuildScriptModel` from `:modules:chain-listener`, but Gradle exposes that tooling task only at the build root. The IDE project configuration links the module as a second standalone Gradle project.

**Approach:** Remove the redundant standalone `modules/chain-listener` Gradle link so the IDE resolves Kotlin DSL tooling through the root Gradle build.

</frozen-after-approval>

## Implementation Notes

- Removed the redundant IntelliJ Gradle link for `modules/chain-listener`; the root build remains the sole linked project.

## Review Triage Log

- `defer` — The chain-listener version and JUnit changes were already present before this fix, and no changes were made to that build script.
- `defer` — The module `.gitignore` and IDEA datasource deletions were already present before this fix and remain untouched.
