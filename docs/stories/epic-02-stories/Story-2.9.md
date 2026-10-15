# Story 2.9 — Implement pre-execution compliance and transfer validation

**Story Points:** 3

As the transfer workflow,
I want authorization, eligibility, compliance, and configuration checked again at the execution boundary,
so that stale approval or a newly applied hold cannot authorize a transfer.

## Acceptance Criteria

- Immediately before submission, the workflow verifies that the instruction remains authorized and eligible and that required compliance decisions remain valid.
- A cancellation, hold, rejection, or terminal outcome prevents submission.
- The network, token contract, transfer limits, and applicable token restrictions are checked against current configured policy.
- A material change to source, destination, amount, token, or network invalidates earlier approval unless an explicit policy permits that change.
- The gate records a structured pass/failure with a stable reason and instruction correlation; failure leaves the instruction non-executable.
- A failed gate starts no blockchain transaction and records no screening approval on its behalf.
- Automated tests cover stale/missing approval, changed instruction data, revoked authorization, active hold, and valid execution eligibility.

## Open Decisions

- Confirm whether screening results expire and the approved policy for any material instruction change.

## Dependencies

- Story 2.2 must provide transfer eligibility validation; Story 2.8 must provide the aggregated compliance outcome.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Phase 6, BR-002, BR-007, FR-002, FR-008
