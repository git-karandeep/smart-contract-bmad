# Story 2.1 — Create transfer instruction API and database model

**Story Points:** 5

As an authorized transfer initiator,
I want to submit a uniquely identified token-transfer instruction,
so that the platform can durably track and orchestrate its lifecycle.

## Acceptance Criteria

- The API accepts the source and destination wallets, token identifier or contract, amount, network, idempotency key, and authenticated initiating principal.
- A valid request creates and persists a durable instruction document in MongoDB with a unique instruction ID, `RECEIVED` status, timestamps, and the data needed to start the workflow.
- Amounts are validated as exact decimal values and are not stored or processed using floating-point arithmetic.
- Malformed, incomplete, or out-of-range requests receive a clear client error and create neither an instruction nor a workflow instance.
- The caller is authenticated, and the initiating principal is recorded; source-wallet authority is enforced before execution.
- A status lookup by instruction ID returns the current persisted state without exposing sensitive identity or signing data.
- API and persistence contracts support explicit lifecycle transitions and the idempotency behavior completed in Story 2.14.

## Dependencies

- None. This establishes the durable instruction used by the remaining stories.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-001, Phase 1, FR-001, FR-012, AC-009, AC-012, AC-013
- Epic implementation plan — [Epic 2 plan](../../../_bmad-output/initiative-bmad-generated/plan-epic-02-wallet.md), API route and validation-error decisions
