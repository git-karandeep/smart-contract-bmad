# Story 2.16 — Verify end-to-end transfer security and failure handling

As a release owner,
I want an automated end-to-end suite for the wallet transfer boundary,
so that authentication, compliance gates, contract execution, and recovery work together without unsafe transfers.

## Acceptance Criteria

- The suite exercises an authorized successful transfer from instruction creation through screening, execution, finality, reconciliation, and completion.
- It proves unauthenticated or unauthorized initiators, invalid instructions, provider-authentication failures, mismatched/duplicate messages, and rejected, review, or timed-out screening cannot submit a blockchain transaction.
- It proves sanctions restrictions, stale compliance, changed transfer data, and newly recorded holds prevent execution.
- It proves contract restrictions reject unauthorized transfers and blockchain revert, unknown outcome, and reconciliation mismatch do not produce a completed status.
- It proves equivalent duplicate instructions and replayed messages do not create a second logical transfer or duplicate token movement.
- Test reports and setup guidance identify required service/provider/chain substitutes and make clear which checks require an integration or controlled network environment.
- The supported focused unit, integration, contract, and end-to-end suites pass; a failure in a covered security assertion fails the release check.

## Open Decisions

- Confirm the supported integration environment and approved provider, signer, and blockchain test fixtures; use test credentials only and never weaken production security controls to make tests pass.

## Dependencies

- Stories 2.1–2.15 must be implemented sufficiently to exercise the end-to-end transfer and security boundaries.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Security Requirements, Error Handling and Recovery, Acceptance Criteria AC-001–AC-015, Definition of Done
