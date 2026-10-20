# Story 2.4 — Initiate KYC screening and correlate results

**Story Points:** 3

As the transfer workflow,
I want to request KYC screening for the parties required by policy,
so that transfers proceed only when their applicable KYC eligibility is approved.

## Acceptance Criteria

- A KYC request is created for each party required by configured policy and is associated with the transfer instruction and a unique screening request ID.
- The request carries only identity references and other fields that the provider is authorized and configured to receive.
- The request records its screening type, correlation identifier, timestamp, and policy/provider version needed to interpret the response.
- The workflow can wait for an asynchronous KYC result correlated to the correct instruction and screening request.
- `APPROVED`, `REJECTED`, `REVIEW_REQUIRED`, and `ERROR` results remain distinct; review or error is never treated as approval.
- Provider failure or an invalid response follows an explicit non-success path and cannot authorize blockchain execution.
- Automated tests cover request creation, correlation data, minimized payloads, and each supported result category.

## Open Decisions

- Confirm which transfer parties and jurisdictions require KYC, the provider contract, and the approved identity-reference fields. Do not infer legal policy or send unnecessary identity data.

## Dependencies

- Story 2.3 must provide the durable screening workflow and result-wait boundary.
 under modules/process-definitions/src/main/resources/processes

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-003, Phase 3, FR-003–FR-004
