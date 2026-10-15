# Story 2.8 — Aggregate compliance decisions and enforce timeout policies

**Story Points:** 5

As the transfer workflow,
I want all mandatory screening results evaluated under one configured policy,
so that incomplete, rejected, or unresolved reviews cannot reach blockchain execution.

## Acceptance Criteria

- The decision service waits for every screening decision marked mandatory by policy, regardless of arrival order.
- All mandatory approvals produce an eligible outcome; any mandatory rejection, review requirement, error, or unresolved timeout produces its distinct configured non-approval outcome.
- A timeout is never treated as an approval, and retry/backoff behavior is bounded by explicit configuration.
- The workflow records the evaluated decisions, policy version, reason, and outcome against the instruction ID.
- When a branch rejects, times out, or enters hold, outstanding subscriptions and branches are completed or cancelled without later releasing execution.
- Automated tests cover out-of-order results, approval, rejection, review, error, timeout, and duplicate result delivery.

## Open Decisions

- Confirm which screening types are mandatory, the policy mapping for each result, timeout durations, retry limits, backoff, and escalation destinations.

## Dependencies

- Stories 2.3–2.7 must provide the screening requests, correlated result ingestion, and durable decisions.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-006, Phase 5, FR-006–FR-007, AC-002–AC-008
