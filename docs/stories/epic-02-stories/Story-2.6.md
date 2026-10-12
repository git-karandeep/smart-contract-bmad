# Story 2.6 — Request sanctions screening for transfer parties

As the transfer workflow,
I want sanctions screening performed for the relevant parties and transaction,
so that a prohibited transfer is stopped and any required restriction is preserved.

## Acceptance Criteria

- The sanctions adapter sends the provider's agreed request contract with instruction and unique screening request IDs, correlation ID, required party/transaction references, screening version, and timestamp.
- The request follows the approved data-minimization and provider-authorization policy.
- `APPROVED`, `REJECTED`, `REVIEW_REQUIRED`, and `ERROR` results are validated and correlated to the correct instruction and sanctions request.
- A sanctions result requiring a freeze, restriction, or other mandated action is surfaced as a distinct policy outcome; it is not reduced to generic approval or silently treated as a generic rejection.
- Unavailable service, malformed responses, and errors remain explicit non-approval outcomes.
- Tests cover decisions, request correlation, provider failure, and any configured restricted/frozen outcome.

## Open Decisions

- Confirm the sanctions provider, authentication and schema, applicable match thresholds, and the policy/action mapping for a possible match, including who may authorize release of a hold.

## Dependencies

- Story 2.3 must provide the workflow service-task and result-correlation boundary.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-005, Phase 3 Sanctions Request, FR-003, FR-004, AC-004
