# Story 2.11 — Enforce token transfer restrictions in Solidity

As an asset administrator,
I want the token contract to enforce its authorized transfer restrictions,
so that the asset cannot be moved through an unauthorized transfer path.

## Acceptance Criteria

- The contract enforces the approved authorization model for transfers and prevents unapproved callers or paths from bypassing applicable restrictions.
- The contract enforces its applicable balance, pause, role, and token-specific transfer restrictions consistently on every supported transfer entry point.
- Restricted, paused, unauthorized, and insufficient-balance transfers revert without changing balances or emitting a successful transfer event.
- Valid authorized transfers preserve the token's expected ERC-20 behavior and emit the correct transfer event and amount.
- Role assignment, revocation, pause/unpause, and other administrative operations are access controlled and covered by tests.
- Solidity tests include both allowed and denied cases for each implemented restriction and verify no unintended state changes on revert.
- Contract source and deployment/configuration guidance identify the chain and token parameters required by the adapter.

## Open Decisions

- Confirm the existing token contract, required on-chain restrictions, roles, administration/upgrade model, and whether compliance is enforced entirely off-chain, on-chain, or by both. Do not infer a new allowlist or compliance method.

## Dependencies

- Story 2.10 must identify the contract interface the adapter will call; contract restrictions must be complete before production transfer enablement.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), Phase 7, Security Requirements, FR-008, Definition of Done
