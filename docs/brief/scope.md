# Brief Scope

## Functional scope

- Create a transfer request through a REST API.
- Validate request data and compliance inputs.
- Execute on-chain settlement through a contract interaction.
- Wait for confirmation depth before finalizing settlement state.
- Publish domain events on each state transition.
- Query status and audit history for a transfer.

## Non-functional scope

- At-least-once event processing with idempotent effects.
- Secure key management with no private keys in application config or logs.
- Scalable workers and consumers without duplicate submissions.
- Observability from business id to transaction hash to chain event.
- Recovery support for network outages, node failures, and chain reorgs.

## Out of scope for the initial version

- Consumer-facing UI work.
- Full multi-chain operational support beyond a single EVM-compatible target.
- Deep wallet and custody product features unrelated to settlement orchestration.
