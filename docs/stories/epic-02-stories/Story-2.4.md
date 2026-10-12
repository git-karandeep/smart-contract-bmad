# Story 2.4 — Request KYC screening for transfer parties

As the transfer workflow,
I want a correlated KYC request sent for the relevant transfer parties,
so that execution depends on their applicable KYC eligibility.

## Acceptance Criteria

- The KYC adapter sends the provider's agreed request contract with the instruction ID, unique KYC request ID, correlation ID, relevant party references, screening version, and request timestamp.
- The request contains only information the KYC provider is authorized to receive; raw identity data is not copied into workflow variables or routine logs.
- The adapter accepts and validates the provider's supported outcomes: `APPROVED`, `REJECTED`, `REVIEW_REQUIRED`, or `ERROR`.
- The request and response are associated with the original instruction and KYC request; a response for another screening type or request cannot satisfy KYC.
- Provider unavailability and malformed responses are represented as explicit operational outcomes, not as approval.
- Contract and adapter tests cover request formation, each supported result, and provider failure.

## Open Decisions

- Confirm the KYC provider, authentication method, request/response schema, relevant-party selection policy, and screening-result validity period.

## Dependencies

- Story 2.3 must provide the workflow service-task and result-correlation boundary.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-003, Phase 3 KYC Request, FR-003, FR-004
