# Story 2.12 — Track blockchain transaction status and finality

As the transfer workflow,
I want submitted transactions monitored through the configured confirmation threshold,
so that submission is not mistaken for successful or final execution.

## Acceptance Criteria

- The listener records each transaction against its instruction, chain ID, transaction hash, and submission time and tracks inclusion and receipt status.
- The transaction lifecycle distinguishes at least pending, confirmed, reverted, replaced, and unknown outcomes.
- A successful receipt is not considered final until the configured network confirmation/finality condition is met.
- RPC or listener outages and timeouts are explicit; an unknown status does not trigger a second transfer.
- Reorganizations, replacement transactions, and receipt changes are handled according to the configured network policy, without a premature completion event.
- Correlated transaction-result events are emitted once per logical state transition and include the authoritative transaction reference.
- Tests cover pending-to-confirmed, revert, timeout/unknown, replacement, and configured finality behavior.

## Open Decisions

- Confirm supported networks, authoritative RPC/provider sources, confirmation/finality thresholds, reorganization policy, and replacement-transaction handling.

## Dependencies

- Stories 2.10 and 2.11 must define transaction submission and the contract transfer event/interface.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-008, Phase 8, FR-009, AC-010, AC-011
