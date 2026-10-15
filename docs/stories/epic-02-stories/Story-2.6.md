# Story 2.6 — Initiate sanctions screening and correlate results

**Story Points:** 3

As the transfer workflow,
I want sanctions screening for parties and transactions required by policy,
so that prohibited transfers are rejected, restricted, frozen, or reviewed as policy requires.

## Acceptance Criteria

- A sanctions request uses a unique screening request ID and is correlated to the specific instruction and screening type.
- Only the parties and transaction information approved for the configured sanctions provider are sent.
- The request records the timestamp, correlation identifier, and policy/provider version required to interpret its result.
- `APPROVED`, `REJECTED`, `REVIEW_REQUIRED`, and `ERROR` results remain distinct and cannot be substituted for one another.
- A sanctions result that requires rejection, restriction, freeze, or review follows its configured outcome and prevents unauthorized execution.
- A provider error, missing result, or invalid correlation never counts as approval.
- Automated tests cover request correlation, minimized payload, response outcomes, and failure paths.

## Open Decisions

- Confirm the applicable sanctions policy, provider contract, and authorized freeze/restriction actions. This story must not invent legal requirements or operational powers.

## Dependencies

- Story 2.3 must provide the durable screening workflow and result-wait boundary.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-005, Phases 3–5, FR-003–FR-004, AC-004
