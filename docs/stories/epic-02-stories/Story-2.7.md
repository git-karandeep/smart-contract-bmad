# Story 2.7 — Ingest authenticated screening results

As a screening provider,
I want to submit authenticated and correlated screening results,
so that the correct transfer workflow can record and evaluate each decision.

## Acceptance Criteria

- Provider messages are authenticated using the configured provider identity mechanism and rejected when authentication or integrity validation fails.
- The ingestion boundary validates the message schema, provider, screening type, instruction ID, screening request ID, correlation ID, decision, and timestamp.
- Unknown, mismatched, expired, or cross-instruction identifiers are rejected or quarantined and cannot satisfy another transfer's screening wait.
- A valid result is durably recorded and correlated to the correct Camunda process using the unique instruction and screening identifiers.
- Duplicate delivery of the same provider/request/message does not create a second logical decision or advance the workflow more than once.
- Late results are associated with the original instruction and cannot automatically release a rejected, cancelled, terminal, or legally held transfer.
- Responses to providers do not disclose whether unrelated instructions or screening requests exist.
- Automated tests cover valid messages, invalid authentication, malformed payloads, mismatched correlation, duplicates, and late results.

## Open Decisions

- Confirm whether provider callbacks, Kafka messages, or both are supported, and define provider-specific authentication, replay protection, message IDs, and retention/quarantine policy.

## Dependencies

- Stories 2.4, 2.5, and 2.6 must define the screening request identifiers and provider contracts.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Phase 4, BR-006, FR-004, FR-005, AC-005, AC-007, AC-008
