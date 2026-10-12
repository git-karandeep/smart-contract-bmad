# Story 2.5 — Request AML screening for transfers

As the transfer workflow,
I want an AML screening request sent with the transfer context required by policy,
so that the transfer cannot proceed when AML review rejects or cannot clear it.

## Acceptance Criteria

- The AML adapter sends the provider's agreed request contract with the instruction ID, unique AML request ID, correlation ID, relevant party/transaction references, screening version, and request timestamp.
- Only information authorized for the AML provider is sent, and sensitive identity data is excluded from workflow variables and routine logs.
- The adapter validates `APPROVED`, `REJECTED`, `REVIEW_REQUIRED`, or `ERROR` responses and binds each response to the matching AML request and instruction.
- Provider errors, unavailable service, and malformed responses are surfaced as explicit non-approval outcomes.
- Automated contract and adapter tests cover all supported decisions, correlation, and provider failure.

## Open Decisions

- Confirm the AML provider, authentication method, request/response schema, transaction context, screening policy/version, and result validity period.

## Dependencies

- Story 2.3 must provide the workflow service-task and result-correlation boundary.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-004, Phase 3 AML Request, FR-003, FR-004
