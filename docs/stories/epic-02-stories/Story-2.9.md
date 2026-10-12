# Story 2.9 — Revalidate transfer immediately before execution

As the transfer workflow,
I want authorization, compliance, and transfer conditions checked again at the execution boundary,
so that stale approval or a newly recorded hold cannot authorize token movement.

## Acceptance Criteria

- Immediately before calling the blockchain adapter, the service verifies the instruction remains authorized, eligible, and in an executable state.
- All mandatory compliance decisions exist, remain valid under configured policy, and refer to the exact source, destination, amount, token, and network being executed.
- A material change to those transfer fields invalidates prior approval unless an explicitly approved policy says otherwise.
- The service checks for cancellation, hold, sanctions restriction, token/network disablement, and applicable current limits and restrictions.
- Any failed or indeterminate revalidation produces an explicit non-executable outcome and prevents blockchain submission.
- Tests prove stale compliance, changed instructions, new holds, and disabled configuration all block execution; valid current state passes.

## Open Decisions

- Confirm the compliance-result expiry/re-screening policy and how concurrent instruction updates, holds, and the final execution gate are serialized.

## Dependencies

- Story 2.8 must produce the compliance decision; Story 2.2 must provide transfer-validation policy.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Phase 6, BR-007, FR-002, FR-008
