# Story 2.13 — Reconcile confirmed transactions and finalize transfer status

As a transfer initiator,
I want the reported transfer outcome verified against authoritative blockchain data,
so that the instruction is marked complete only when the expected token movement is confirmed.

## Acceptance Criteria

- Reconciliation runs only after the configured successful finality condition, and obtains authoritative transaction, receipt, and token-event data.
- The reconciler verifies the expected chain, transaction hash, contract, source, destination, token, and exact amount against the instruction.
- The instruction becomes `COMPLETED` and a completion event/notification is emitted only when every reconciliation check succeeds.
- A reverted transaction is recorded as failed and is never marked complete.
- Missing or mismatched receipt/event data places the instruction in an investigation or hold state, raises an operational signal, and does not report success.
- Completion updates and notifications are correlated to the instruction and are safe against duplicate event delivery.
- Tests cover a matching transfer, revert, missing event, wrong amount/address/contract, and recovery from a reconciliation mismatch.

## Open Decisions

- Confirm the authoritative data source and operational investigation/resolution procedure for discrepancies.

## Dependencies

- Story 2.12 must provide a transaction that has met the configured finality condition.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Phase 9, FR-010, AC-001, AC-010, AC-014
