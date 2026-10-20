# Story 3.6 — Produce a machine-readable generation report

As a workflow maintainer,
I want each generation run to produce a machine-readable report,
so that extracted processes and validation results can be inspected automatically.

## Acceptance Criteria

- A generation run produces a machine-readable report that identifies its input model, generated parent and called-process artifacts, extracted reusable processes, and validation results.
- The report distinguishes validation errors from warnings and does not report success when BPMN generation or validation fails.
- The report schema is stable and has an explicit version so consuming automation can detect incompatible changes.
- Automated tests cover successful generation, validation failures, and report serialization.

## Open Decisions

- The epic source ends mid-sentence at “A machine-readable report identifying extrac”; confirm the complete report requirements and schema before implementation.
- Assumption: the minimum report includes the input model, generated artifact names, extracted processes, and validation results; confirm or revise this proposal when the missing source text is restored.

## Dependencies

- Stories 3.1–3.5 must establish the generation outputs and validation behavior the report summarizes.

## References

- Epic — [Standardized BPMN Design and Reusable Workflow Generator](../../epics/epic-03-wallet-standardization.md)
