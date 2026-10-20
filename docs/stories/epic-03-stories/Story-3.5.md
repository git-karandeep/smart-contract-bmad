# Story 3.5 — Preserve safe payment recovery in generated BPMN

As the transfer workflow,
I want generated BPMN to preserve the existing payment idempotency and recovery contract,
so that standardization cannot cause duplicate on-chain transfers.

## Acceptance Criteria

- The generated payment path preserves the existing instruction-scoped idempotency identity and does not implement a second idempotency mechanism.
- An unknown submission outcome follows the existing status-lookup or reconciliation path and cannot trigger a blind second submission.
- The generated path preserves the existing recovery policy for any later submission and keeps business rejection distinct from technical failure.
- Automated process tests prove that standardization does not bypass the compliance and pre-execution gates or duplicate payment submission.

## Open Decisions

- Confirm that the recovery contract already established by Epic 02 remains authoritative; this story does not redefine the evidence that authorizes resubmission.

## Dependencies

- Stories 3.3 and 3.4 must establish the generated parent and its standardized failure paths because this story updates the same BPMN artifact.
- Epic 02 Stories 2.3 and 2.14 must provide the existing payment workflow and idempotency/recovery contract.

## References

- Epic — [Standardized BPMN Design and Reusable Workflow Generator](../../epics/epic-03-wallet-standardization.md)
- Existing workflow — [Story 2.3: Create Camunda token movement BPMN workflow](../epic-02-stories/Story-2.3.md)
- Existing idempotency scope — [Story 2.14: Implement idempotency, retries, and recovery](../epic-02-stories/Story-2.14.md)
- Architecture — [Worker idempotency and failure handling](../../architecture.md), Section 7.3
