---
title: 'Add BPMN diagram interchange to generated sanctions screening process'
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

**Problem:** Generated `sanctions-screening-process.bpmn` has no BPMN diagram, plane, shapes, or edges, so editors cannot render its process.

**Approach:** Extend the BPMN generator to emit deterministic BPMN DI for every sanctions process node and sequence flow, and test complete diagram coverage and Camunda validation.

</frozen-after-approval>

## Implementation Notes

The tagged path is generated output; modify the generator and tests, regenerate through Gradle, and do not hand-edit build output. Reuse the AML diagram geometry and DI helper methods, adapting BPMN element IDs for the sanctions process.

Added BPMNDI generation for the sanctions process: its plane covers all 9 flow nodes and all 10 sequence flows. Generator tests assert plane binding, full shape/edge coverage, bounds, waypoints, and the gateway marker.

`:modules:process-definitions:check` regenerated the tagged BPMN, passed Camunda model validation, and passed all process-definition tests.

The quick review found no significant issues. No commit was made because the worktree contains existing uncommitted user changes that must remain intact.
