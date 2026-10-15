# Story 2.5 — Initiate AML screening and correlate results

**Story Points:** 3

As the transfer workflow,
I want to request AML screening using the configured transfer context,
so that transfers with unacceptable AML outcomes cannot reach execution.

## Acceptance Criteria

- An AML request is created with a unique screening request ID and the instruction and correlation identifiers required to associate its response.
- The request includes only transaction and party context authorized for the configured AML provider.
- The request records the screening type, timestamp, and policy/provider version needed to interpret its result.
- Asynchronous AML results are returned to the correct instruction and screening wait.
- `APPROVED`, `REJECTED`, `REVIEW_REQUIRED`, and `ERROR` results remain distinct, and only configured acceptable results can satisfy the AML gate.
- Provider or transport failure follows a non-success path; missing or erroneous screening is never interpreted as approval.
- Automated tests cover request formation, correlation, response classification, and provider failure.

## Open Decisions

- Confirm the AML provider contract, required data fields, and acceptable-decision policy before enabling a live provider.

## Dependencies

- Story 2.3 must provide the durable screening workflow and result-wait boundary.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-004, Phases 3–5, FR-003–FR-004, AC-003
