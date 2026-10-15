# Story 2.7 — Implement authenticated screening-message ingestion

**Story Points:** 5

As a screening provider and the transfer platform,
I want screening results authenticated, validated, correlated, and deduplicated,
so that only the correct provider decision can advance its transfer.

## Acceptance Criteria

- The ingestion boundary authenticates the configured provider and rejects unauthenticated or invalid messages.
- Each message is validated for schema, integrity, expected provider, screening type, instruction ID, screening request ID, and correlation identifier.
- Unknown, expired, or mismatched request identifiers are rejected or quarantined and cannot authorize another instruction.
- Duplicate messages are recognized by stable provider/request/message identity and produce no second decision or workflow side effect.
- A valid result is durably recorded before workflow correlation and produces an auditable event associated with the instruction.
- Late results cannot release a transfer already rejected, cancelled, terminal, or under a legally required hold.
- Automated tests cover authentication, invalid correlation, duplicate, late, malformed, and valid messages.

## Open Decisions

- Confirm provider authentication mechanisms, message schemas, stable message identifiers, and quarantine handling for each live integration.

## Dependencies

- Stories 2.3–2.6 must define the workflow wait and screening-request identities this boundary validates.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Phase 4, FR-004–FR-005, AC-005, AC-007–AC-008
