# Story 3.1 — Build the BPMN standardization generator

As a workflow developer,
I want to parse and standardize the existing transfer BPMN model,
so that later transformations start from a validated, deterministic process.

## Acceptance Criteria

- The generator accepts the existing `transfer-lifecycle-source.bpmn` model and produces a valid Camunda 8 BPMN model with the declared naming and process-ID conventions applied.
- Analysis identifies the service tasks, gateways, message events, timers, subprocesses, and the KYC, AML, and sanctions capabilities selected for extraction.
- Unsupported or ambiguous structures are reported for review and are never silently rewritten as reusable processes.
- Invalid or unsupported BPMN input produces actionable validation errors and does not produce a success-shaped output.
- Standardization leaves business behavior, sequence flows, message correlations, timers, and error paths unchanged unless a later story explicitly changes them.
- Repeated generation from the same input produces equivalent BPMN output.
- Automated tests cover valid input, invalid input, deterministic output, and preservation of the existing process topology.

## Open Decisions

- Choose the generator's invocation form and generated-artifact destination; neither is defined in the epic.

## Dependencies

- None.

## References

- Epic — [Standardized BPMN Design and Reusable Workflow Generator](../../epics/epic-03-wallet-standardization.md)
- Architecture — [Camunda conventions and process versioning](../../architecture.md), Sections 7.1 and 7.6
- Architecture — [Process testing strategy](../../architecture/coding-standards.md)
- Existing process — `../../../modules/process-definitions/src/main/resources/processes/transfer-lifecycle-source.bpmn`
