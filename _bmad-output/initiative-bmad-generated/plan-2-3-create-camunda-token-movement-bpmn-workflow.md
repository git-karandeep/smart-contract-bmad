---
title: 'Create Camunda token movement BPMN workflow'
type: 'feature'
ticket: 'Story-2.3'
created: '2026-10-15'
status: 'built'
baseline_revision: '06e1e254313e11227377cc2711e21a7118d12b48'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/stories/epic-02-stories/Story-2.3.md'
  - '{project-root}/docs/epics/epic-02-wallet.md'
  - '{project-root}/_bmad-output/initiative-bmad-generated/plan-epic-02-wallet.md'
  - '{project-root}/_bmad-output/initiative-bmad-generated/plan-2-2-validate-wallet-addresses-and-transfer-eligibility.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The repository has a deployable Camunda 8 token-transfer process, but parts of Story 2.3 are only represented by static BPMN wiring and XML-shape tests. Cancellation and operational-error behavior are not explicit, and tests do not yet prove that workflow triggers and correlated waits are safely scoped to one instruction.

**Approach:** Complete and verify the existing BPMN lifecycle, preserving the pinned Camunda 8.9.0 message-correlation mechanism, unique instruction-based process correlation, parallel screening and timeout aggregation, fail-closed execution gates, transaction outcomes, and reconciliation. Add explicit cancellation/operational failure handling only within the agreed process boundary.

## Boundaries & Constraints

**Always:** Use instruction ID plus screening-specific correlation keys, minimum workflow variables, bounded worker retries, explicit non-executable failure routes, and existing worker/status contracts. Preserve all already-completed Story 2.1–2.2 behavior and external provider/security policy boundaries.

**Never:** Add provider integrations, new API security policy, legal/compliance decisions, chain-observer behavior, or blind transaction retry. Do not claim workflow startup is crash-recoverable beyond its current persisted launch semantics.

**Decision (2026-10-15):** Add a correlated cancellation message and worker, accepted only before blockchain submission; the authorized upstream publisher/API is outside this story. Cancellation must win or lose atomically against the submission transition: if submission has started, leave the process running to monitor the transaction.

</frozen-after-approval>

## Code Map

- `modules/process-definitions/src/main/resources/processes/transfer-lifecycle.bpmn` — executable process: message start, validation, parallel KYC/AML/sanctions, event-based waits, timeout persistence, aggregate, pre-execution gate, submission, transaction wait, reconciliation, terminal paths.
- `modules/process-definitions/src/test/java/ai/generalcontracts/startup/workflow/TransferLifecycleBpmnTest.java` — XML assertions for process topology and fail-closed paths; extend to cover subscription correlation, safe variable scope, cancellation/error paths, and all joins.
- `modules/process-workers/src/test/java/ai/generalcontracts/startup/workflow/TransferLifecycleWorkerCoverageTest.java` — ensures each service-task type has a registered worker and verifies screening joins/default gateways.
- `modules/process-workers/src/main/java/ai/generalcontracts/startup/workflow/TransferLifecycleCamundaWorkers.java` — worker task types and instruction-ID input contract.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/transfer/CamundaTransferWorkflowStarter.java` — publishes the unique `TransferInstructionReceived` start message with the instruction ID and KYC/AML/sanctions correlation keys only.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/transfer/CamundaScreeningWorkflowCorrelator.java` — publishes typed screening results with provider/message deduplication and screening-specific correlation.
- `modules/process-workers/src/main/java/ai/generalcontracts/startup/workflow/TransferLifecycleJobHandlers.java` and `TransferWorkerConfiguration.java` — workflow task decisions, persisted failures, and fail-closed policies; preserve these contracts.
- `gradle/libs.versions.toml` and `deployment/docker-compose.yml` — both pin Camunda/Zeebe 8.9.0; use this existing runtime and message API.

## Tasks & Acceptance

**Execution:**
- [x] Tighten the workflow start/message contract and tests so a transfer is correlated by instruction ID, screening waits use per-type keys, and workflow state contains no wallet or secret data that workers can reload from Mongo.
- [x] Add the `TransferCancellationRequested` message path and worker; atomically allow cancellation only before submission, and preserve the transaction-monitoring process if submission wins the race.
- [x] Make operational errors explicit: bounded job retries must leave a visible Camunda incident/non-success process state; add assertions for those retry declarations and ensure failure cannot route to completion.
- [x] Strengthen BPMN and worker coverage for parallel joins, event-based result-vs-timeout subscription cleanup, retry/incident behavior, and each non-executable terminal route.

**Acceptance Criteria:**
- Given a valid transfer instruction, when the Camunda 8.9 process starts, then it is correlated to exactly that instruction and reaches the parallel screening waits only after Story 2.2 validation passes.
- Given asynchronous screening results, when they arrive in any order or a timeout wins, then each message correlates to its own instruction and screening type, the losing event subscription is cancelled, and the join cannot proceed until every required branch has a result or persisted hold.
- Given an invalid, rejected, held, cancelled, operationally failed, or unreconciled transfer, when the corresponding BPMN path completes or incidents, then it cannot reach blockchain submission or successful completion.
- Given worker retries are exhausted or the transaction outcome is non-final, when Camunda processes the task, then the process remains explicitly non-successful and does not blindly resubmit.

## Implementation Notes

The story asks for a complete durable lifecycle and explicit cancellation/error paths; the current BPMN already implements much of the success and screening-failure topology. Cancellation is a correlated process message only; authorization and publication are owned by a future upstream API. The process must not interrupt transaction monitoring when submission has already begun.

## Verification

**Commands:**
- `.\gradlew.bat :modules:process-definitions:test :modules:process-workers:test :modules:contracts:test --no-daemon` — expected: BPMN shape, worker coverage, and workflow correlation tests pass.
- `git diff --check` — expected: no whitespace errors.

## Review Triage Log

- **Finding:** Accepted cancellation ended only the non-interrupting event subprocess, leaving the main process active at screening waits and retaining their subscriptions/timers.
- **Resolution:** Route only `cancellationOutcome = "CANCELLED"` to a BPMN terminate end event, which terminates the process instance and its outstanding waits. A rejected cancellation follows the event subprocess's ordinary end path, preserving the main process and transaction monitoring if submission won the atomic race.
- **Regression coverage:** `TransferLifecycleBpmnTest` asserts the cancellation start remains non-interrupting, accepted cancellation selects the terminate end event, and rejected cancellation selects the ordinary event-subprocess end. `TransferLifecycleJobHandlersTest` covers successful cancellation and rejection after submission starts or wins the race.
- **Verification:** `.\gradlew.bat :modules:process-definitions:test :modules:process-workers:test :modules:contracts:test --no-daemon` passed after the fix; `git diff --check` passed.
