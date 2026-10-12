---
title: 'Implement Epic 2 wallet-based token movement'
type: 'feature'
ticket: ''
created: '2026-10-11'
status: 'built'
baseline_revision: '9c0cfacc1520eb1169b1e5e8dabc95cd6a3c88f5'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/epics/epic-02-wallet.md'
  - '{project-root}/docs/stories/epic-02-stories/'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The repository has infrastructure scaffolding but no durable, end-to-end token-transfer lifecycle, leaving validated instructions, screening, execution, finality, and reconciliation unimplemented.

**Approach:** Implement Stories 2.1–2.16 across the existing modules, using durable instruction and external-side-effect state, correlated asynchronous workflow steps, fail-closed compliance gates, and authoritative chain reconciliation.

## Boundaries & Constraints

**Always:** Preserve all pre-existing staged, unstaged, and untracked work. Keep the existing security configuration and security behavior unchanged. Use exact decimal/integer arithmetic, durable idempotency, explicit non-success outcomes, authenticated/correlated screening messages, minimal sensitive data, and no secrets in workflow variables or logs.

**Never:** Weaken or bypass current Spring Security, change security routes/policies/profile settings, invent provider/legal/sanctions policies, assume a submission is final, or resubmit an uncertain blockchain transaction without reconciling it.

**Decisions (2026-10-11):** Use `/api/transfers` with standard validation errors; target the repository-pinned Camunda 8 dependency and its supported message-correlation mechanism; use provider-neutral screening ports with authenticated Kafka ingestion and test adapters until provider contracts are supplied; reuse existing entitlement checks and require explicit fail-closed eligibility/compliance policy configuration; reuse existing token/chain integration patterns with external network/signer configuration and fail closed when policy is missing; use existing observability/Kafka patterns with append-only audit records and configurable alert hooks; use local fakes/test-chain E2E and document real providers/signers/networks as externally gated; preserve existing security behavior.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid transfer | Authorized instruction; every screening approves; transaction reconciles | One logical transfer reaches `COMPLETED` after configured finality | Persist and correlate each transition |
| Non-approved screening | Rejection, review, error, or timeout | No blockchain submission; explicit held/review/rejected outcome | Bounded retry only; preserve reason and alert |
| Uncertain chain result | Submission timeout or missing/mismatched receipt/event | Never report completion or blindly resubmit | Persist unknown/hold state for reconciliation |
| Duplicate request/message | Same idempotency key or provider message delivered again | Return/apply existing logical result once | Reject conflicting transfer data |

</frozen-after-approval>

## Code Map

- `settings.gradle` — declares the existing contracts, chain-adapter, persistence-mongo, process-definitions, process-workers, domain-service, api-gateway, app-bootstrap, and solidity-contract modules.
- `modules/contracts/.../controller/OrderController.java`, `service/OrderService.java`, `repository/OrdersRepository.java`, and `model/Orders.java` — API/Mongo patterns exist but model only orders; add transfer-specific contracts without disturbing security endpoints.
- `modules/process-definitions` and `modules/process-workers` — Gradle shells; add BPMN and worker orchestration with instruction-keyed correlation.
- `modules/domain-service` and `modules/persistence-mongo` — intended lifecycle/persistence seams; add state transitions, idempotency, screening records, transaction state, audit/outbox as required.
- `modules/chain-adapter/.../ChainRpcClient.java` — currently reads EVM chain status only; extend via explicit chain/signer/contract ports after decisions.
- `modules/solidity-contract/src/main/solidity/TransferToken.sol` — existing token source is the contract baseline; do not invent compliance restrictions.
- `modules/contracts/.../messaging/` and `observability/` — Kafka event and correlation/audit patterns to reuse; `tests/e2e-smoke.sh` and module Gradle tasks are existing verification surfaces.
- Preserve `SecurityConfiguration.java`, `application*.yaml` security settings, `SecurityAuditLogger.java`, `RequestCorrelationFilter.java`, `deployment/docker-compose.yml`, and existing security tests/docs. Existing unrelated edits/deletions and untracked story inputs are user-owned; do not reset, stage, or overwrite them.

## Tasks & Acceptance

**Execution:**
- [ ] `modules/contracts`, `modules/domain-service`, `modules/persistence-mongo` — add validated transfer API/status, exact amount model, durable state transitions, idempotency claim, and persistence tests (2.1, 2.2, 2.14).
- [ ] `modules/process-definitions`, `modules/process-workers`, `modules/domain-service` — implement deployable BPMN, instruction-keyed screening orchestration, bounded timeouts, approval aggregation, pre-execution gate, cancellation/hold and recovery paths (2.3–2.9, 2.14).
- [ ] `modules/contracts`, `modules/domain-service`, `modules/chain-adapter`, `modules/solidity-contract` — implement provider ports/adapters and authenticated result ingestion; add exact-unit Web3j submission, contract restrictions based only on approved requirements, transaction tracking/finality and reconciliation (2.4–2.7, 2.10–2.13).
- [ ] `modules/domain-service`, `modules/persistence-mongo`, `modules/contracts` — persist auditable correlated decisions, deduplicate events, expose operational signals and sanitize sensitive data (2.7–2.8, 2.13–2.15).
- [ ] `modules/*/src/test`, `tests/` — add focused unit/contract/integration and end-to-end coverage for success, denial, replay, concurrency, unknown outcome, recovery, and reconciliation mismatch (2.1–2.16).

**Acceptance Criteria:**
- Given an invalid, unauthorized, held, or non-approved instruction, when processing reaches any later workflow boundary, then no blockchain submission occurs.
- Given equivalent concurrent requests or duplicate/late messages, when processed or recovered, then one logical instruction and at most one authorized transaction intent are retained.
- Given an approved instruction, when signed submission, configured finality, and receipt/event reconciliation succeed, then completion is emitted once; any revert, uncertainty, or mismatch remains explicitly non-complete.
- Given screening or chain provider failure, when bounded retries expire, then the transfer is held with reason, correlation, and operational signal.
- Given the implementation and tests, when security configuration is compared with baseline, then the existing security configuration and behavior remain unchanged.

## Implementation Notes

Implemented the transfer API/domain/persistence foundations, BPMN and registered Camunda workers, fail-closed screening request/result boundaries, exact-unit Web3j adapter and submission journal, transaction finality/reconciliation policy, and focused contract tests. Real provider policies, signer/RPC configuration, chain observation, the `/api/transfers` authorization decision, and completion-event outbox remain unconfigured or deferred; this implementation must not be represented as production-ready.

## Plan Change Log

## Review Triage Log

- **high — patch —** `TransferLifecycleJobHandlers.reconcile`: a `PENDING` transaction outcome was terminally held, so a later finality result could not complete the workflow. Patched to persist `TRANSACTION_PENDING`, expose a pending outcome, and loop to the result catch event; regression tests cover pending then confirmed.
- **high — patch —** `TransferLifecycleJobHandlers.submit`: an `UNKNOWN` response discarded a known transaction hash, preventing reconciliation. Patched to persist and return the hash; regression test verifies both.
- **high — patch —** `TransferLifecycleJobHandlers.submit` and `Web3jErc20TransferAdapter.submit`: interrupted submissions could not recover an existing journaled hash and were forced into an unknown state. Added durable journal lookup and verified matching intent before reusing a recorded submission, without resubmission.
- **medium — patch —** `TransactionFinalityPolicy.evaluate`: reverted receipts bypassed configured confirmation depth and could be treated as final prematurely. Reverts now remain pending until the configured depth is satisfied.
- **high — defer —** `TransferController` at `/api/transfers` is denied by the existing `SecurityConfiguration` catch-all rule even when enabled. The user's explicit requirement to keep security configuration unchanged prevents adding route authorization; a separate approved security-policy decision is required before API use.
- **medium — defer —** `TransferLifecycleService.startWorkflow`: a crash after persisting `STARTING` but before starting Camunda leaves the launch claim unresolved. Safe recovery requires querying/reconciling Camunda using the approved runtime's durable process identity semantics; no such runtime recovery contract is configured.
- **high — defer —** The existing transaction-result catch event has no configured chain observer/message correlator and can wait indefinitely after submission. The available listener code has only evaluation/reconciliation logic, and no approved network/event-source configuration is supplied; completing this requires that external integration and a bounded hold policy.
- **medium — defer —** Reconciliation marks the instruction `COMPLETED` but does not publish a durable transfer-completion event; BPMN only assigns `completionEmitted=true`. A durable completion outbox and approved event contract/delivery semantics are still needed.

## Design Notes

Keep external providers and chain access behind typed ports; use fakes only in tests. Persist before publishing events or starting external side effects, and make retries replay the recorded logical operation. Existing security configuration is a hard invariant, not an implementation target.

## Verification

**Commands:**
- `.\gradlew.bat :modules:contracts:test :modules:contracts:apiTest :modules:contracts:integrationTest :modules:chain-adapter:test :modules:chain-listener:test :modules:domain-service:test :modules:process-definitions:test :modules:process-workers:test --no-daemon --rerun-tasks` — passed.
- `npm run contracts:test` — passed (4 Solidity tests).
- `git diff --check -- modules/chain-adapter modules/chain-listener modules/contracts modules/domain-service modules/persistence-mongo modules/process-definitions modules/process-workers` — passed.
- `.\gradlew.bat e2eTest --no-daemon` — not run successfully; environment lacks a working Docker Desktop Linux engine. Live MongoDB, Kafka, Camunda, provider, and chain paths remain unverified.
