# Story 2.16 — Complete end-to-end transfer security testing

**Story Points:** 5

As a product and compliance stakeholder,
I want the complete transfer lifecycle exercised across success and failure paths,
so that the platform demonstrates safe orchestration from instruction through reconciliation.

## Acceptance Criteria

- An end-to-end test proves an authorized, eligible instruction proceeds through mandatory approvals, submission, configured finality, and matching reconciliation to one completion.
- End-to-end tests prove validation failure, screening rejection/review/timeout, unauthorized initiation, transaction revert, unknown outcome, and reconciliation mismatch never produce a false completion or unauthorized submission.
- Duplicate requests and messages, late results, and recovery after interruption preserve a single logical transfer and transaction intent.
- Tests verify sensitive identity and signing data are absent from API responses, workflow variables, logs, and audit payloads.
- The suite uses deterministic provider fakes and a local/test chain or equivalent isolated integration environment; production credentials and networks are not required.
- Test setup and execution are documented, repeatable in CI, and report failures with instruction/correlation identifiers but no secrets.

## Open Decisions

- Confirm the supported CI integration environment and whether the final suite must exercise a deployed Camunda/Mongo/Kafka stack or may use approved in-process/test adapters for selected boundaries.

## Dependencies

- Stories 2.1–2.15 must provide the contracts, workflow, policy gates, adapters, persistence, finality, reconciliation, and audit behavior under test.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Definition of Done, Non-Functional Requirements, AC-001–AC-015
