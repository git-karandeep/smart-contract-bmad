# Story 2.14 — Implement idempotency, retries, and recovery

**Story Points:** 5

As the platform,
I want duplicate requests, retries, and workflow restarts handled safely,
so that transient failures cannot cause unintended duplicate token movement.

## Acceptance Criteria

- Repeated requests with the same idempotency key and equivalent normalized transfer data return the existing instruction/status and do not start a second logical transfer.
- Reuse of an idempotency key with materially different source, destination, amount, token, or network is rejected and does not change the original instruction.
- Idempotency claims are durable and atomic across concurrent requests and service restarts.
- Screening messages and workflow events are deduplicated by their stable provider/request/message identities and cannot apply a decision twice.
- Retries are bounded, use configured backoff, and preserve a single logical instruction and transaction intent.
- After an uncertain blockchain submission, recovery queries/reconciles the original transaction before any resubmission decision; no timeout alone causes another transfer.
- Workflow restart recovery uses persisted state and external-side-effect records and does not assume interrupted work had no side effects.
- Tests cover concurrent duplicate submissions, conflicting payload, duplicate messages, retry exhaustion, restart recovery, and unknown transaction outcome.

## Open Decisions

- Confirm idempotency-key scope/retention, request canonicalization rules, retry limits/backoff, and the authorized procedure for deciding whether a transaction may be resubmitted after reconciliation.

## Dependencies

- Stories 2.1, 2.3, 2.7, 2.10, and 2.12 must provide the durable instruction, workflow, message, submission, and transaction state used for recovery.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-009, Phases 1, 4, 8, FR-005, FR-012, Error Handling and Recovery, AC-007, AC-011–AC-013
