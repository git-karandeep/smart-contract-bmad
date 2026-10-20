---
title: 'Restore BPMN diagram interchange for generated transfer lifecycle'
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

**Problem:** The generated `transfer-lifecycle.bpmn` omits the source BPMN diagram, leaving its process without a BPMNDiagram or BPMNPlane and making it unrenderable in BPMN editors.

**Approach:** Preserve and update the source transfer diagram during generation, replace removed screening internals with called-process shapes, and add layout for the unknown-submission recovery route. Validate that the generated diagram references every flow node and sequence flow exactly once and passes Camunda model validation.

</frozen-after-approval>

## Implementation Notes

Updated `BpmnWorkflowGenerator.generateParent` to retain the source plane, prune shapes/edges for extracted screening nodes, add screening-call and payment-recovery shapes, reroute affected edges, and remove stale DI references. The generator test checks full DI coverage in addition to the recovery route and confirms the removed KYC subprocess internals do not linger in the parent plane.

`:modules:process-definitions:check` passes; the generator validates every generated BPMN with Camunda's BPMN model validator. `transfer-lifecycle.bpmn` has a collaboration-bound plane with complete node and sequence-flow geometry.

Quick review found no significant issues. No commit was made to preserve existing uncommitted changes.
