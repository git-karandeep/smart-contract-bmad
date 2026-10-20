# Story 3.2 — Generate reusable screening processes

As a workflow developer,
I want KYC, AML, and sanctions screening represented by reusable processes with explicit contracts,
so that each compliance capability can be deployed and invoked independently.

## Acceptance Criteria

- Generation produces `kyc-screening-process.bpmn`, `aml-screening-process.bpmn`, and `sanctions-screening-process.bpmn` as independently deployable processes.
- Each process has a stable kebab-case process ID and a documented versioning strategy.
- Each process defines its required inputs, outputs, outcome codes, and correlation identifiers without relying on undeclared parent-process variables.
- The process interface is agreed and recorded before generation, including variable ownership and timeout/failure semantics.
- Each process models its request and result boundary while keeping the three screening outcomes distinct.
- Automated tests verify all three generated process definitions and their declared contracts.

## Open Decisions

- The exact input/output variable names, outcome-code vocabulary, and deployed-version strategy must be agreed and documented as part of this story before the generated contracts are treated as stable.

## Dependencies

- Story 3.1 must provide the validated generator and BPMN output path.

## References

- Epic — [Standardized BPMN Design and Reusable Workflow Generator](../../epics/epic-03-wallet-standardization.md)
- Architecture — [Camunda conventions](../../architecture.md), Section 7.1
- Existing workflow — [Story 2.3: Create Camunda token movement BPMN workflow](../epic-02-stories/Story-2.3.md)
