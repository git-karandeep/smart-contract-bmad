---
title: 'Add BPMN diagram interchange to generated KYC screening process'
type: 'bugfix'
ticket: ''
created: '2026-10-20'
status: 'built'
baseline_revision: '6ed3508e70398cef9c7059cd13990f962a646874'
route: 'oneshot'
route_source: 'auto'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Generated `kyc-screening-process.bpmn` has no BPMN diagram, plane, shapes, or edges, so editors cannot render its process and multi-instance subprocess.

**Approach:** Extend the BPMN generator to emit deterministic BPMN DI covering all KYC process and embedded subprocess nodes and flows, with tests for complete diagram coverage and Camunda validation.

</frozen-after-approval>

## Implementation Notes

The tagged path is generated output; edit the generator and tests, regenerate via Gradle, and do not hand-edit build output. The AML generator now has BPMNDI helper methods to reuse. The source transfer BPMN already defines KYC subprocess layout coordinates that can guide the standalone process diagram.

Added deterministic BPMNDI for the KYC process plane, all 12 nodes (including expanded multi-instance subprocess internals), and all 13 sequence flows. Test assertions verify complete geometry and subprocess expansion.

`:modules:process-definitions:test` regenerated the tagged BPMN and passed the generator's Camunda model validation and DI coverage checks.

The quick review found no significant issues. No commit was made because this worktree contains earlier uncommitted user changes that must remain untouched.
