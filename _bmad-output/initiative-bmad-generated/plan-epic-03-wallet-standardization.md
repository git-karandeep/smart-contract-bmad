---
title: 'Implement Epic 03 BPMN standardization generator'
type: 'feature'
ticket: 'epic-03-stories'
created: '2026-10-20'
status: 'in-review'
baseline_revision: '6ed3508e70398cef9c7059cd13990f962a646874'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 1
context:
  - 'docs/epics/epic-03-wallet-standardization.md'
  - 'docs/stories/epic-03-stories/'
  - 'docs/architecture.md'
  - 'modules/process-definitions/src/main/resources/processes/transfer-lifecycle.bpmn'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The transfer BPMN implements screening and payment paths in one process, with no generator, reusable screening process contracts, or automated generation report. The Epic 03 source is truncated in its final report requirement.

**Approach:** Build a deterministic BPMN generator that validates the current process, extracts KYC, AML, and sanctions into independently deployable processes, and replaces those paths with mapped Call Activities. Preserve fail-closed screening and payment behavior, apply agreed retry and incident policies, produce a versioned report, and verify the generated process set.

**Decision (2026-10-20):** Invoke generation through a Gradle task and package generated BPMN under the build output; do not overwrite the checked-in source model.
**Decision (2026-10-20):** Preserve `retries="3"` for ordinary service tasks and `retries="1"` for payment submission, apply capped exponential backoff of 1s, 2s, and 4s to retryable worker failures, and retain 15-minute screening waits. Payment recovery must not automatically broadcast again.
**Decision (2026-10-20):** Add instruction-keyed recovery through the existing submission journal and reconcile only when it provides a known transaction hash; when no hash is recorded, leave the transfer on operational hold and never resubmit.
**Decision (2026-10-20):** The versioned report contains the input model, generated artifact names, extracted reusable processes, and validation results; no additional fields are in scope without restoring the missing epic text.
**Decision (2026-10-20):** Proceed with implementation despite the dirty worktree, preserving all existing changes outside the approved Epic 03 implementation.

## Boundaries & Constraints

**Always:** Preserve instruction-based correlation, policy-driven KYC, parallel screening, 15-minute screening waits, fail-closed outcomes, pre-execution checks, payment idempotency, and existing worker ownership. Keep process variables limited to IDs and small state. Use Camunda 8.9 and Java 21 conventions already in the repository.

**Never:** Put business logic in BPMN workers, expose identity payloads or secrets in process variables, blindly retry an uncertain chain submission, treat a missing screening response as approval, or change unrelated worktree edits.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid source | Existing transfer lifecycle BPMN | Standardized parent, three reusable screening processes, and a versioned machine-readable report | Report validation failures; no partial success |
| Invalid or unsupported BPMN | Malformed XML or unsupported process structure | Actionable diagnostic; no successful generated set | Fail generation without overwriting valid outputs |
| Screening failure | Technical error, business rejection, or timeout | Bounded retries for transient errors; explicit non-success result for rejection/timeout | Exhausted retries remain operationally visible |
| Unknown payment submission | Submission outcome cannot be established | Reconcile with a journaled transaction hash or keep the transfer on operational hold | Never rebroadcast or report success when no hash is known |

</frozen-after-approval>

## Code Map

- `modules/process-definitions/src/main/resources/processes/transfer-lifecycle.bpmn` — input process; three screening branches, message subscriptions, timers, join, and payment paths. Its `submissionUnknown` end currently has no status-lookup path.
- `modules/process-definitions/src/main/java/` — no generator exists; module has only the JDK and JUnit dependencies.
- `modules/process-definitions/src/test/java/ai/generalcontracts/startup/workflow/TransferLifecycleBpmnTest.java` — secure JAXP parsing and XPath shape assertions; use as the structural validation baseline.
- `modules/process-workers/src/main/java/ai/generalcontracts/startup/workflow/TransferLifecycleJobHandlers.java` — persists screening and transfer states; retries are not implemented here. Submission uncertainty sets `TRANSACTION_UNKNOWN`; reconciliation requires a transaction hash and chain outcome.
- `modules/process-workers/src/main/java/ai/generalcontracts/startup/workflow/TransferLifecycleCamundaWorkers.java` — registered worker types must continue matching every service task.
- `modules/process-workers/src/main/java/ai/generalcontracts/startup/workflow/TransferSubmissionPort.java` and `TransferEventReconciliationAdapter.java` — current submit and known-hash reconciliation boundaries.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/transfer/MongoSubmissionJournal.java` and `modules/persistence-mongo/src/main/java/ai/generalcontracts/startup/transfer/TransferSubmissionRecord.java` — durable instruction-keyed submission journal; unknown RPC responses can have no hash.
- `modules/process-workers/src/main/java/ai/generalcontracts/startup/workflow/ScreeningRequestService.java` and `ScreeningDecisionAggregator.java` — durable request/correlation and fail-closed outcome behavior.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/transfer/CamundaTransferWorkflowStarter.java` — current instruction, AML, and sanctions variables; KYC request IDs are returned by its worker.
- `modules/process-workers/src/test/java/ai/generalcontracts/startup/workflow/TransferLifecycleWorkerCoverageTest.java` — service-task/worker parity, screening joins, and fail-closed gateway invariants.
- `docs/architecture.md` Sections 7.1, 7.3, and 7.6; `docs/architecture/coding-standards.md` — BPMN naming, worker failure, versioning, and process-test conventions.
- `deployment/docker-compose.yml` and `gradle/libs.versions.toml` — optional Camunda 8.9 workflow runtime and pinned client version.
- `docs/stories/epic-03-stories/Story-3.1.md`–`Story-3.8.md` — ordered story scope, including cleanup and Camunda process verification.

## Tasks & Acceptance

**Execution:**
- [x] Add a secure, deterministic generator and Gradle-task integration in `modules/process-definitions`; package outputs from build-generated resources without overwriting source, reject invalid or unsupported BPMN, and report identified structures.
- [x] Define screening input/output contracts from persisted worker behavior; generate all three called processes, then update the parent Call Activities and mappings without changing screening semantics.
- [x] Standardize worker retry/backoff and incident behavior; preserve the single-attempt payment submission boundary and agreed recovery for unknown outcomes.
- [x] Emit a versioned report for generated artifacts, extracted capabilities, and validation; leave no successful report after failure.
- [x] Add structural and Camunda process tests for generated artifacts, screening outcomes, retry exhaustion, and uncertain payment results; compile the opt-in Camunda test suite.
- [x] Run focused module tests and the repository Gradle check.
- [ ] Execute the opt-in Camunda process tests in a Docker-enabled environment.
- [x] Complete a behavior-preserving refactor sweep after implementation findings; run final affected-module checks.

**Acceptance Criteria:**
- Given the current BPMN input, when generation succeeds, then the deployable parent and KYC, AML, and sanctions process files have explicit contracts, Call Activity mappings, and a versioned report.
- Given malformed or unsupported BPMN, when generation runs, then it returns actionable errors and no success-shaped or partial artifact set.
- Given screening rejection, missing results, timeout, or exhausted technical retries, when the generated workflow runs, then it cannot reach payment submission as approved.
- Given a business rejection, when its worker fails, then the workflow follows its business outcome without technical retry/backoff.
- Given an unknown chain submission outcome, when recovery runs, then it cannot blindly resubmit and follows the approved lookup/reconciliation or hold behavior.
- Given the generated process set, when Camunda process tests execute, then valid and non-success paths preserve correlation, timeout, aggregation, and terminal-outcome behavior.

## Implementation Notes

- Quick review hardening: source validation now checks the critical validation, screening aggregation, compliance, execution, submission, and reconciliation routes, their authorization conditions, and fail-closed gateway defaults. Regression tests reject a validation-success route redirected straight to payment submission, an unknown submission redirected to completion, and a fail-open execution default.
- Camunda process tests now deploy the generated parent and all three screening processes and cover success, rejection, missing results, timeout, exhausted retries, and both known-hash and no-hash payment recovery. The opt-in suite compiles, but Docker is unavailable for runtime execution.
- Chain transaction events now publish `TransferTransactionResult` messages: `businessId` supplies the instruction correlation key, and submitted, confirmed, failed, and reorged topics map to pending, confirmed, reverted, and unknown outcomes respectively.

## Plan Change Log

- User-approved continuation: connect the existing chain transaction topics to Camunda workflow correlation using `PlatformEvent.businessId` as the transfer instruction ID; preserve explicit pending, finalized, reverted, and uncertain outcomes.
- Quick review hardening: restrict `validateTransfer` to its sole expected outgoing sequence flow and add a regression test rejecting a direct bypass to successful completion.

## Review Triage Log

- **Quick / HIGH — resolved:** The generator did not validate critical validation-to-screening, compliance, and execution-gate routes. Source validation now checks required safety-critical sequence-flow endpoints, authorization conditions, and fail-closed gateway defaults, including payment and reconciliation routes; regression tests confirm that bypassing screening, treating an unknown submission as completion, and failing open at the execution gate are rejected with a failed report and no generated parent.
- **Quick / MEDIUM — incomplete acceptance criterion:** The suite still has no Camunda engine process test exercising the generated parent and screening processes together. Docker is unavailable in this environment, so correlation, timeout, aggregation, and terminal-outcome runtime behavior remain unverified; the existing structural and worker tests do not replace that acceptance criterion.
- **Quick / HIGH — patch (resolved):** An additional outgoing flow from `validateTransfer` could skip `validationGateway` and reach successful completion without screening. Validation now enforces the sole expected `flowValidateGateway`; a regression test rejects the bypass and confirms no generated parent is published.
- **Quick / MEDIUM — defer (carried):** carried — the Camunda process suite is now implemented and compiles, but its engine behavior remains unverified because the local Docker daemon is unavailable. Run `:modules:process-workers:camundaProcessTest` in a Docker-enabled environment to settle the acceptance criterion.

## Design Notes

The generator must treat the source model as input and produce an all-or-nothing artifact set. Existing worker services own persistence, provider calls, and chain effects; called processes should orchestrate those workers and return only agreed small outcome variables. Package generated resource outputs into the application build. Unknown payments may be reconciled only from a transaction hash already present in the instruction-keyed journal; without one, keep an operational hold.

## Verification

**Commands:**
- `.\gradlew.bat :modules:process-definitions:test :modules:process-workers:test :modules:contracts:test :modules:process-workers:compileCamundaProcessTestJava --no-daemon` — expected: generator, BPMN, worker parity, correlation, recovery, and process-test compilation pass.
- `.\gradlew.bat :modules:process-workers:camundaProcessTest --no-daemon` — opt-in Camunda process execution; requires Docker.
- `.\gradlew.bat check --no-daemon` — expected: all project checks pass.

**Observed:** Focused generator, worker, contract, and process-test compilation checks passed, as did `check --no-daemon`. The opt-in Camunda process test task was attempted but could not initialize Testcontainers because the Docker Desktop Linux engine pipe is unavailable; process-level runtime behavior remains unverified.
