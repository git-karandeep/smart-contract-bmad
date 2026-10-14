---
title: 'Create transfer instruction API and persistence model'
type: 'feature'
ticket: 'Story-2.1'
created: '2026-10-14'
status: 'built'
baseline_revision: '59f94a89e4912345c437ed64d5bc85bbaa2d035d'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/stories/epic-02-stories/Story-2.1.md'
  - '{project-root}/docs/epics/epic-02-wallet.md'
  - '{project-root}/_bmad-output/initiative-bmad-generated/plan-epic-02-wallet.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 2.1 requires an authenticated, durable transfer-instruction API, but its route and error contract are only resolved by the broader Epic 2 plan and the existing API route may remain denied by the current security policy.

**Approach:** Reuse the existing transfer API, Mongo instruction model, and exact-decimal handling; close only verified Story 2.1 gaps and cover the request, persistence, and status-query behavior with focused tests.

## Boundaries & Constraints

**Always:** Preserve unrelated staged and unstaged changes. Keep amounts exact, record the authenticated principal, persist a `RECEIVED` instruction before workflow launch, return only non-sensitive status data, and use the existing transfer lifecycle and idempotency contracts. Allow authenticated JWT principals to reach only the transfer routes; enforce per-wallet access with the existing entitlement guard.

**Never:** Broaden access to other routes or weaken JWT/X.509 protections; implement the remainder of Story 2.14; alter other transfer lifecycle stories; modify unrelated `_bmad-output` walkthrough files.

**Decision (2026-10-14):** Expose `POST /api/transfers` and `GET /api/transfers/{instructionId}` to authenticated JWT principals, relying on the existing `AssetEntitlementGuard` for per-wallet authorization.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid create | Complete authenticated request with valid exact amount | Persist one `RECEIVED` instruction with ID, timestamps, principal, and workflow data | Return the established create response |
| Invalid create | Missing, malformed, or out-of-range field/amount | No instruction or workflow launch | Clear client validation error |
| Unauthorized source | Authenticated principal lacks source-wallet authority | No execution is authorized | Reject before execution |
| Status lookup | Existing instruction ID | Return persisted state without identity or signing data | Use established not-found behavior |
| Duplicate instruction | Existing idempotency key and equivalent instruction | Preserve the single logical instruction and lifecycle contract | Reject conflicting data without mutating original |

</frozen-after-approval>

## Code Map

- `modules/contracts/src/main/java/ai/generalcontracts/startup/controller/TransferController.java` — existing `POST /api/transfers` and `GET /api/transfers/{instructionId}` surfaces; preserve established API patterns.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/model/TransferRequest.java` and `TransferResponse.java` — request validation and response exposure boundary; amounts are represented as exact decimal text.
- `modules/persistence-mongo/src/main/java/ai/generalcontracts/startup/transfer/TransferInstruction.java` and `TransferInstructionRepository.java` — Mongo instruction document, lifecycle fields, and idempotency lookup/index.
- `modules/domain-service/src/main/java/ai/generalcontracts/startup/transfer/TransferLifecycleService.java` — canonical amount validation, persistence, and workflow-launch boundary.
- `modules/contracts/src/test/java/ai/generalcontracts/startup/controller/TransferControllerTest.java`, `modules/domain-service/src/test/java/ai/generalcontracts/startup/transfer/TransferLifecycleServiceTest.java`, and `modules/contracts/src/test/java/ai/generalcontracts/startup/SecurityConfigurationTest.java` — focused API, lifecycle, and security coverage.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/config/SecurityConfiguration.java` — deny-by-default policy; add only the approved authenticated transfer-route access.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/security/ClientCertificateFilter.java` — existing client-certificate requirement for protected routes; include transfer routes without changing other filtering.

## Tasks & Acceptance

**Execution:**
- [x] `TransferController`, `TransferRequest`, `TransferResponse`, and focused controller tests — verify/fill the create and status API contracts, authenticated-principal recording, safe response projection, and client-error behavior.
- [x] `TransferInstruction`, `TransferInstructionRepository`, `TransferLifecycleService`, and lifecycle tests — verify/fill durable `RECEIVED` persistence, exact amount normalization, validation-before-side-effect ordering, and transition/idempotency compatibility.
- [x] `SecurityConfiguration`, `ClientCertificateFilter`, and `TransferRouteSecurityTest` — allow authenticated JWT principals with the existing client-certificate protection to reach only transfer routes; verify unauthenticated/non-JWT/no-certificate denial, per-wallet entitlement enforcement, and unchanged protections elsewhere.

**Acceptance Criteria:**
- Given a complete valid authenticated request, when it is submitted, then one uniquely identified `RECEIVED` instruction with timestamps and workflow data is persisted.
- Given a malformed, incomplete, or out-of-range request, when it is submitted, then a clear client error is returned and neither an instruction nor workflow instance is created.
- Given an amount with decimal precision, when validated and persisted, then its value remains exact and is never processed as floating point.
- Given an authenticated principal, when the instruction is created and later executed, then the initiating principal is retained and source-wallet authority is checked before execution.
- Given an existing instruction ID, when status is requested, then the persisted lifecycle state is returned without sensitive identity or signing data.
- Given lifecycle transitions and duplicate instruction requests, when they are applied, then the API and persistence contracts support explicit transitions and the idempotency behavior assigned to Story 2.14.

## Implementation Notes

The existing controller, exact-decimal validation, Mongo instruction document, lifecycle persistence, and idempotency behavior already met the story contract, so those implementations were retained. Added narrowly scoped authenticated-JWT access for transfer create/status routes while preserving the existing client-certificate requirement, plus route-security and ownership/entitlement tests. The API remains gated by `app.transfer.api.enabled=true`; deployments without that property keep the controller disabled. Unrelated pre-existing walkthrough edits were preserved.

## Plan Change Log

## Review Triage Log

- **medium — patch —** Transfer access initially bypassed the existing `ClientCertificateFilter` because its path predicate excluded `/api/transfers/**`; a JWT caller without a client certificate could reach the newly authorized endpoints. Added the transfer paths to the protected filter scope and regression tests for POST and GET without a certificate.

## Verification

**Commands:**
- `.\gradlew.bat :modules:contracts:test :modules:domain-service:test :modules:contracts:apiTest :modules:contracts:integrationTest --no-daemon` — passed.
- `git diff --check -- modules/contracts/src/main/java/ai/generalcontracts/startup/config/SecurityConfiguration.java modules/contracts/src/main/java/ai/generalcontracts/startup/security/ClientCertificateFilter.java modules/contracts/src/test/java/ai/generalcontracts/startup/TransferRouteSecurityTest.java modules/contracts/src/test/java/ai/generalcontracts/startup/controller/TransferControllerTest.java _bmad-output/initiative-bmad-generated/plan-2-1-create-transfer-instruction-api-and-persistence-model.md` — passed; the untracked test emitted a line-ending warning.
