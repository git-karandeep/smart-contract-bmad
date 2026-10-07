---
title: 'Establish BMAD project structure and traceability'
type: 'chore'
ticket: 'Story-0.2'
created: '2026-10-07'
status: 'built'
baseline_revision: '32d439ba4fbe543627717124bf80d6bba3a081c9'
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

**Problem:** Story 0.2 calls for a consistent BMAD artifact layout and traceability, but its proposed paths overlap an already established repository layout and use a stale `.bmad/` path. Without a clear map, developers cannot reliably follow requirements through epics, stories, design, code, and tests.

**Approach:** Preserve the active `_bmad/`, `.bmad-core/`, and existing docs/module layouts; add concise navigation and traceability documentation, compatibility pointers for the requested legacy paths, and a root test index that points to module-local tests. Do not duplicate configuration, security policy, or test sources.

</frozen-after-approval>

## Implementation Notes

One-shot is appropriate: the missing work is documentation and navigation, not a new runtime feature. Existing user changes in `.idea/` and `modules/chain-listener/build.gradle` will remain untouched.

- Added indexes linking Epic 0 and its stories; documented a factual requirement-to-design-to-source/test path without implying that existing tests cover documentation acceptance criteria.
- Added compatibility pointers for `.bmad/` and `docs/architecture/security.md`; canonical BMAD configuration and security policy remain in their existing locations.
- Added the requested root `tests/` entry point while keeping tests beside their Gradle modules.
- Quick review findings were corrected: the traceability example now distinguishes the Story 0.2 module path from the wider multi-module codebase, and the README now links the decisions index.

## Review Triage Log

- `medium` — Patched `docs/traceability.md` to identify `modules/contracts/src/` as the initial path required by Story 0.2, not the entire application source tree, and clarify the test link. The repository is multi-module and the existing test does not verify the documentation acceptance criteria.
- `low` — Patched `README.md` to link `docs/decisions/README.md`; the new decision index was otherwise undiscoverable from the top-level documentation map.
