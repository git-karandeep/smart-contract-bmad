# Story 3.4 — Standardize screening retries and failures

As a compliance operations team,
I want screening retries, timeouts, and exhausted failures handled consistently,
so that technical problems never become implicit approvals.

## Acceptance Criteria

- Transient technical failures use an explicit finite retry limit and backoff, while business rejections follow business outcome paths and are not retried as technical failures.
- Missing screening responses and screening timeouts produce explicit non-success outcomes and can never be interpreted as approvals.
- Exhausted retries produce an operationally visible incident or recovery state rather than a successful screening result.
- Generated screening processes retain the defined correlation identifiers and do not leave abandoned response subscriptions on timeout or failure.
- Automated tests verify the configured retry bound and cover transient failure, business rejection, timeout, and retry exhaustion.

## Open Decisions

- Record the chosen retry limits, backoff values, and operational recovery procedure in versioned configuration or operations documentation; the epic requires bounded retries and recovery but does not specify these values.

## Dependencies

- Story 3.3 must provide the parent and called-process flow to standardize.

## References

- Epic — [Standardized BPMN Design and Reusable Workflow Generator](../../epics/epic-03-wallet-standardization.md)
- Architecture — [Worker failure handling and process operations](../../architecture.md), Sections 7.3 and 7.6
- Architecture — [Process testing strategy](../../architecture/coding-standards.md)
