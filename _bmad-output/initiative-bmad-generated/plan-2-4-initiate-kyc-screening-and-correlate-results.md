---
title: 'Initiate KYC screening and correlate results'
type: 'feature'
ticket: 'Story-2.4'
created: '2026-10-15'
status: 'built'
baseline_revision: '31f5432608a63e5eab81de72f48e969b1dab2cc6'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/stories/epic-02-stories/Story-2.4.md'
  - '{project-root}/docs/epics/epic-02-wallet.md'
  - '{project-root}/_bmad-output/initiative-bmad-generated/plan-2-3-create-camunda-token-movement-bpmn-workflow.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The workflow creates one generic KYC request per transfer, while Story 2.4 requires requests for each policy-required party and an asynchronous result correlated to that request. Transfer records contain wallet references but no jurisdiction or verified identity references, so concrete parties, provider fields, and legal requirements cannot be inferred safely.

**Approach:** Extend the existing screening boundary to support policy-selected KYC subjects, request-specific correlation, durable request metadata, and fail-closed results without choosing a legal policy or enabling an unapproved provider. Preserve the existing distinct result decisions and keep AML/sanctions behavior unchanged.

## Boundaries & Constraints

**Always:** Use explicit configured policy to identify required KYC subjects and provider-authorized data; distinguish absent/error policy from an explicit policy decision that KYC is not required; persist unique request and correlation identifiers, timestamps, and policy/provider version; keep missing, rejected, review, and error outcomes non-approving.

**Never:** Infer jurisdictions, required parties, identity fields, or provider contract; publish wallet, principal, or identity data unless explicitly approved; weaken result authentication/correlation or alter unrelated AML/sanctions behavior.

**Decision (2026-10-15):** Build a provider-neutral KYC extension point that remains disabled by default. Configured policy supplies required subjects and opaque allowlisted references; no provider or legal policy is selected, and absent/incomplete policy or provider configuration fails closed.

</frozen-after-approval>

## Code Map

- `modules/process-workers/src/main/java/ai/generalcontracts/startup/workflow/ScreeningRequestService.java` — durable create/publish flow and timestamps; extend its KYC path without changing generic AML/sanctions semantics.
- `modules/process-workers/src/main/java/ai/generalcontracts/startup/workflow/ScreeningRequestPolicyResolver.java` and `ScreeningRequestPolicy.java` — current provider/version-only policy boundary; current default in `TransferWorkerConfiguration` is unavailable/fail-closed.
- `modules/process-workers/src/main/java/ai/generalcontracts/startup/workflow/ScreeningRequestMessage.java` and `ScreeningRequestPublisher.java` — current minimal provider-neutral Kafka envelope; add only the approved KYC reference shape.
- `modules/persistence-mongo/src/main/java/ai/generalcontracts/startup/transfer/ScreeningRequestRecord.java` and `ScreeningRequestRepository.java` — current instruction/type uniqueness permits one request per type and must support multiple KYC subjects.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/transfer/CamundaScreeningWorkflowCorrelator.java` and `ScreeningResultIngestionService.java` — authenticate, validate, persist, deduplicate, and correlate asynchronous results; correlate KYC by its unique request, not only the transfer/type.
- `../../modules/process-definitions/src/main/resources/processes/transfer-lifecycle-source.bpmn` and `TransferLifecycleCamundaWorkers.java` — existing KYC request and wait path; allow all policy-required KYC requests to complete before aggregation while preserving fail-closed joins.
- `modules/process-workers/src/test/java/ai/generalcontracts/startup/workflow/ScreeningRequestServiceTest.java`, `ScreeningResultValidatorTest.java`, and `ScreeningDecisionAggregatorTest.java`; `modules/contracts/src/test/java/ai/generalcontracts/startup/transfer/ScreeningResultIngestionServiceTest.java` — extend request, correlation, decision, and provider-failure coverage.

## Tasks & Acceptance

**Execution:**
- [x] Add a KYC-specific policy result for required subjects and provider-authorized reference data; treat missing policy, incomplete subject data, and provider/version absence as durable non-success without publishing.
- [x] Persist one unique screening request per required subject with distinct request/correlation IDs, requested timestamp, and policy/provider version; update indexes and result validation to distinguish every request for the transfer.
- [x] Publish only policy-approved KYC references and metadata; preserve the existing minimal non-KYC request contract.
- [x] Correlate each KYC result to its request and instruction and ensure the workflow joins only after all required KYC results or an explicit persisted hold; retain decision categories without treating review/error as approval.
- [x] Add tests for multiple required subjects, minimized payload, unique correlation, all four KYC decisions, duplicate/invalid results, and unavailable policy/provider failures.

**Acceptance Criteria:**
- Given configured policy explicitly identifies required KYC parties, when a transfer enters screening, then one durable request per party carries only approved references and can be correlated by its unique request ID.
- Given asynchronous KYC results for multiple parties, when results arrive in any order, then each is applied only to its matching request and the workflow cannot pass its KYC gate until all required results are approved.
- Given `REJECTED`, `REVIEW_REQUIRED`, or `ERROR`, when the result is processed, then that outcome remains distinct and cannot authorize execution.
- Given missing policy/provider configuration, invalid subject data, provider failure, or invalid result correlation, when KYC is attempted, then the request follows an explicit non-success path and blockchain execution remains blocked.

## Implementation Notes

Added `KycScreeningPolicy` as a provider-neutral resolver result. Requests are durable per subject, use stable request IDs derived from the instruction and opaque subject reference, and carry only policy-approved references; the default resolver remains unavailable. KYC results and timeouts correlate by request ID, and the multi-instance BPMN wait joins only after each required subject result or timeout. KYC result messages use a 15-minute Camunda TTL so a fast provider response can wait for its subscription to open. Explicit `NOT_REQUIRED` is recorded separately and only skips KYC when returned by configured policy. Updated Mongo uniqueness and startup migration to replace the legacy instruction/type index.

The policy/provider contract is an extension point only; no legal rules or live provider are configured. Validation verified unit and contract tests plus BPMN XML topology; live Camunda deployment/runtime and production Mongo migration behavior were not exercised.

## Plan Change Log

## Review Triage Log

**Verdicts:** 0 high, 1 medium, 0 low, 0 false, 0 maybe-false.

- **medium — patch:** `CamundaScreeningWorkflowCorrelator` originally published KYC results without a TTL, so Zeebe discarded a fast response received before the BPMN message subscription opened; ingestion then marked it correlated and duplicate retries could not recover it. Added a 15-minute message TTL matching the KYC wait window and asserted it in `CamundaWorkflowCorrelationTest`; Camunda 8.9 message semantics document that unmatched messages are buffered only for a positive TTL ([messages documentation](https://docs.camunda.io/docs/components/concepts/messages/)). Verification: `.\gradlew.bat :modules:process-workers:test :modules:contracts:test :modules:process-definitions:test --no-daemon` passed.

## Design Notes

The existing screening path already persists request/provider/version metadata, validates result identity and type, authenticates results, and fails closed on publication or result errors. The KYC-specific change must account for multiple party requests in both persistence uniqueness and process correlation; a single transfer/type correlation key is insufficient.

## Verification

**Commands:**
- `.\gradlew.bat :modules:process-workers:test :modules:contracts:test :modules:process-definitions:test --no-daemon` — expected: KYC request, correlation, result-decision, fail-closed, and BPMN join tests pass.
- `git diff --check` — expected: no whitespace errors.
