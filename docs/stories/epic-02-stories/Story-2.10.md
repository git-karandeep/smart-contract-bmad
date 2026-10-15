# Story 2.10 — Implement Web3j blockchain adapter

**Story Points:** 5

As the transfer workflow,
I want approved instructions submitted through a configured blockchain adapter,
so that only validated transfers reach the authorized token contract.

## Acceptance Criteria

- The adapter accepts only an instruction that has passed the pre-execution gate and uses the configured network, token contract, and authorized signer.
- Amounts are converted to integer token base units using the token's configured precision, without floating-point arithmetic or silent rounding.
- The adapter submits the appropriate configured contract operation and records the submission outcome and transaction hash when available.
- Signing keys and secret material are never placed in workflow variables, API responses, or logs.
- A submission failure or uncertain response is represented as a non-final outcome; submission acknowledgement alone is not reported as transfer completion.
- Repeated worker execution cannot submit a second logical transaction without the idempotency/recovery protections.
- Automated tests cover exact amount conversion, network/contract mismatch, denied execution, successful submission, revert, and unknown submission outcome.

## Open Decisions

- Confirm signer custody, supported networks, contract-call policy, and deployment configuration before enabling production submission.

## Dependencies

- Stories 2.3 and 2.9 must provide the workflow submission boundary and a successful pre-execution gate.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Phase 7, BR-007–BR-008, FR-008–FR-009, AC-001, AC-010–AC-011
