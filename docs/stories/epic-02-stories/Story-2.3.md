# Story 2.3 — Orchestrate the token movement lifecycle in Camunda

As a compliance operations team,
I want a durable Camunda workflow for each validated transfer,
so that screening, execution, and recovery follow explicit and auditable paths.

## Acceptance Criteria

- A version-controlled Camunda 8 BPMN process starts for a validated instruction using its unique instruction ID as the business key.
- The process models validation, parallel screening requests, asynchronous result waits, decision aggregation, pre-execution validation, transfer submission, confirmation, reconciliation, and terminal outcomes.
- Screening waits use message names and unique instruction/screening identifiers; wallet address alone is never used as a correlation key.
- The process defines explicit rejection, manual-review, timeout, operational-error, cancellation/hold, transaction-failure, and reconciliation-mismatch paths.
- Parallel branches converge only when required results are collected or the process has entered an explicit hold or failure path; abandoned subscriptions are cleaned up.
- Workflow variables contain only the minimum information needed to execute the process and never contain private keys or unnecessary identity data.
- BPMN deployment and process tests prove a valid transfer reaches its screening wait state and invalid input cannot reach blockchain execution.

## Open Decisions

- Confirm the Camunda 8 deployment/runtime version and supported message-correlation mechanism before finalizing BPMN and worker contracts.

## Dependencies

- Stories 2.1 and 2.2 must provide durable instructions and a validation result.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Phases 1–9, Sections 8–9, FR-003, FR-006, FR-007
