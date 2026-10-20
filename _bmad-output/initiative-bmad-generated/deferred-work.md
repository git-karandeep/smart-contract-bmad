- source_plan: none
  summary: Complete Story 0.2 by establishing the BMAD project structure and requirement-to-code traceability.
  evidence: Split from the requested epic sequence so Story 0.1 repository initialization can be completed and verified independently.
- source_plan: none
  summary: Complete Story 0.3 by verifying and finishing the Gradle Java project and its test setup.
  evidence: Split from the requested epic sequence so Story 0.1 repository initialization can be completed and verified independently.
- source_plan: `_bmad-output/initiative-bmad-generated/plan-0-6-through-0-16-epic-00-foundation.md`
  summary: Pin every GitHub Actions dependency to immutable commit SHAs.
  evidence: The workflow retains version tags for several official actions; Trivy is commit-pinned, and a repository-wide pinning policy is deferred for a separate CI hardening change.
- source_plan: `_bmad-output/initiative-bmad-generated/plan-0-6-through-0-16-epic-00-foundation.md`
  summary: Make MongoDB socket read timeouts profile-configurable and tune them against expected query durations.
  evidence: The shared client customizer applies a five-second read timeout to every profile; production query latency requirements are not defined in this infrastructure foundation.
- source_plan: `_bmad-output/initiative-bmad-generated/plan-epic-02-wallet.md`
  summary: Connect an approved chain observer and Camunda transaction-result correlator, with a bounded persisted hold path when no finality result arrives.
  evidence: The workflow waits for `TransferTransactionResult`; the available chain listener has no observer or message publisher, and no network/event-source configuration or timeout policy was approved.
- source_plan: `_bmad-output/initiative-bmad-generated/plan-epic-02-wallet.md`
  summary: Add a durable, idempotent transfer-completion outbox and publish the approved completion event.
  evidence: Reconciliation persists `COMPLETED` and BPMN sets `completionEmitted=true`, but no durable completion event contract or delivery/deduplication semantics are implemented.
- source_plan: `_bmad-output/initiative-bmad-generated/plan-epic-02-wallet.md`
  summary: Obtain an approved security-policy decision that makes `/api/transfers` reachable without weakening deny-by-default authorization.
  evidence: Existing `SecurityConfiguration` denies every route not explicitly listed, including `/api/transfers`; the current task explicitly requires that security configuration remain unchanged.
- source_plan: `_bmad-output/initiative-bmad-generated/plan-epic-02-wallet.md`
  summary: Define and implement safe recovery for Camunda workflow launches left in `STARTING`.
  evidence: A process restart after persisting the launch claim can leave it unresolved; recovery needs runtime-backed lookup or durable idempotency semantics that are not configured or approved.
- source_plan: `_bmad-output/initiative-bmad-generated/plan-aml-screening-bpmndi.md`
  summary: Keep completed transfers reconcilable after a chain reorg and persist the resulting status correction.
  evidence: The chain reorg event maps to an `UNKNOWN` workflow message, but completed instances have ended and the terminal replay path accepts a matching completed transaction hash without checking for reorg state.
