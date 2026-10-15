# Story 2.11 — Implement and test Solidity transfer restrictions

**Story Points:** 3

As a token administrator and transfer participant,
I want the token contract to enforce its configured transfer controls,
so that unauthorized transfer paths cannot bypass required on-chain restrictions.

## Acceptance Criteria

- The contract enforces the approved balance, allowance, pause, role, and transfer restrictions for every supported transfer entry point.
- Restricted or unauthorized transfers revert with a stable, testable outcome and do not change balances or emit a successful transfer event.
- Authorized transfers retain the token's existing expected behavior when no restriction applies.
- Administrative controls are limited to the approved roles and cannot silently bypass mandatory restrictions.
- Contract tests cover allowed and denied transfers, role boundaries, paused state, and relevant edge cases.
- The deployed contract interface and adapter call assumptions are documented and consistent.

## Open Decisions

- Confirm which regulatory and product restrictions must be enforced on-chain and the authorized roles and exception policy. Do not invent compliance policy or legal restrictions.

## Dependencies

- Story 2.10 must establish the adapter's contract-call needs; this story must not weaken existing contract safeguards.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Phase 7, BR-007, FR-008, Security Requirements, contract execution guidance
