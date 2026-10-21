# Story 3.3 — Invoke screening through Call Activities

As the transfer workflow,
I want the parent process to invoke the reusable screening processes,
so that compliance checks share stable interfaces without changing transfer behavior.

## Acceptance Criteria

- The generated `transfer-lifecycle-source.bpmn` invokes the KYC, AML, and sanctions processes through Call Activities with explicit process IDs and input/output mappings.
- Each mapping passes only the variables required by the called process and returns the declared outcome to the parent.
- Existing sequence flows, parallel screening behavior, correlation identifiers, timeout boundaries, and terminal outcomes are preserved.
- Generated parent and called processes validate together, and tests fail when a call target or required mapping is missing.

## Dependencies

- Stories 3.1 and 3.2 must provide the generator and reusable process contracts.

## References

- Epic — [Standardized BPMN Design and Reusable Workflow Generator](../../epics/epic-03-wallet-standardization.md)
- Existing workflow — [Story 2.3: Create Camunda token movement BPMN workflow](../epic-02-stories/Story-2.3.md)
- Architecture — [Process variables and versioning](../../architecture.md), Sections 7.1 and 7.6
