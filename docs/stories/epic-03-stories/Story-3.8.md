# Story 3.8 — Verify generated workflows in Camunda

As a workflow maintainer,
I want the generated parent and reusable BPMN processes tested together,
so that deployment and process outcomes remain correct across the generated workflow set.

## Acceptance Criteria

- An automated Camunda process test generates, deploys, and validates the parent and all three reusable screening processes as one process-level integration.
- Tests cover successful screening and transfer progression, rejection, timeout, retry exhaustion, and incident or recovery behavior.
- An unknown payment outcome is shown to enter reconciliation without blindly resubmitting.
- The generated workflow cannot reach successful completion when a required screening result is missing or unsuccessful.
- API-to-chain end-to-end testing remains outside this epic; this story verifies the generated BPMN and its Camunda process behavior.

## Dependencies

- Story 3.7 must complete before the closing end-to-end suite.

## References

- Epic — [Standardized BPMN Design and Reusable Workflow Generator](../../epics/epic-03-wallet-standardization.md)
- Architecture — [Process and end-to-end testing strategy](../../architecture/coding-standards.md)
- Existing process — `modules/process-definitions/src/main/resources/processes/transfer-lifecycle.bpmn`
