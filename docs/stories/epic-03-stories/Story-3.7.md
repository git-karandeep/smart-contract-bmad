# Story 3.7 — Refactor sweep

As a workflow maintainer,
I want implementation and review cleanup consolidated after the generator work,
so that the completed feature remains maintainable without changing its behavior.

## Acceptance Criteria

- Cleanup is limited to duplication, naming, and maintainability issues identified during Stories 3.1–3.6 and their reviews.
- No BPMN behavior, process contract, retry policy, or report requirement changes as part of the sweep.
- All affected module checks and tests pass after the cleanup.

## Dependencies

- Stories 3.1–3.6 must be complete before this sweep begins.

## References

- Epic — [Standardized BPMN Design and Reusable Workflow Generator](../../epics/epic-03-wallet-standardization.md)
- Architecture — [Coding and process testing standards](../../architecture/coding-standards.md)
