# Story 2.13 — Implement reconciliation and final transfer status

**Story Points:** 5

As a transfer operations team,
I want a confirmed chain transaction reconciled against its instruction,
so that a transfer is marked complete only when authoritative chain data matches.

## Acceptance Criteria

- Reconciliation reads authoritative transaction and receipt data after the configured finality condition is met.
- It verifies the expected network, transaction, token contract, transfer event, source, destination, and exact amount against the persisted instruction.
- Only a successful matching reconciliation moves the instruction to `COMPLETED` and emits the completion outcome once.
- Revert, missing data, or any mismatch leaves the instruction explicitly non-complete, records a reason, and raises the configured investigation signal.
- Reconciliation is repeatable without duplicate completion events or invalid lifecycle transitions.
- Automated tests cover matching results, mismatch in each material field, missing/reverted receipts, and replay.

## Open Decisions

- Confirm the authoritative chain observation source and operational alert destination for reconciliation mismatches.

## Dependencies

- Stories 2.10 and 2.12 must provide the recorded transaction and finality result.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Phase 9, BR-008, BR-010, FR-010–FR-011, AC-014–AC-015
