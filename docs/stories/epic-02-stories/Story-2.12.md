# Story 2.12 — Track blockchain transaction status and finality

**Story Points:** 5

As the transfer workflow,
I want each submitted transaction monitored through configured finality,
so that submission, inclusion, execution, and confirmation are never conflated.

## Acceptance Criteria

- The transaction monitor tracks the recorded transaction hash and correlates observations to the instruction and network.
- It distinguishes pending, included, successful, reverted, replaced, and unknown outcomes according to the configured chain behavior.
- Confirmation or finality is determined by the configured network policy; RPC acceptance or transaction inclusion alone is insufficient.
- Results are persisted and published with stable transaction and instruction identifiers so the workflow can resume safely.
- A timeout or unknown result triggers reconciliation of the original transaction and never causes automatic blind resubmission.
- Automated tests cover pending-to-final transitions, revert, replacement, RPC failure, and unknown outcome.

## Open Decisions

- Confirm supported networks, block-confirmation or finality policy, transaction replacement rules, and chain observation mechanism.

## Dependencies

- Story 2.10 must persist a transaction submission and its hash when available; Story 2.3 must provide the durable outcome wait.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Phase 8, BR-008, FR-009, AC-010–AC-011
