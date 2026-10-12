# Story 2.10 — Submit compliant transfers through a Web3j adapter

As the transfer workflow,
I want an authorized adapter to submit the validated transfer to the configured Solidity contract,
so that approved instructions can move tokens on the intended network.

## Acceptance Criteria

- The adapter accepts only a revalidated executable instruction and checks configured chain ID, token contract, and supported contract method before preparing a transaction.
- The amount is converted to integer token base units using the configured token decimals with exact arithmetic; floating-point conversion is not used.
- The adapter invokes the configured transfer method and applies signer authorization and nonce/submission controls appropriate to the network.
- Signing keys are obtained from an approved protected key source and are never returned to Camunda or written to logs.
- The transaction hash and submission status are durably recorded and correlated to the instruction before a success-shaped submission result is returned.
- Reverted submission, RPC failure, and uncertain outcome are represented distinctly; an uncertain outcome does not trigger an unverified second transfer.
- Contract, unit, and integration tests verify correct destination, base-unit amount, chain/contract validation, authorized signing, and error handling.

## Open Decisions

- Confirm the signer/key-management service, contract ABI and authorized transfer method, RPC configuration, gas policy, token decimal source, and nonce strategy.

## Dependencies

- Story 2.9 must establish the pre-execution gate.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-007–BR-008, Phase 7, FR-008, FR-012
