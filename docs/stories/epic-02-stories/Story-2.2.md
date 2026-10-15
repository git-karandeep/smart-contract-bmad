# Story 2.2 — Validate wallet addresses and transfer eligibility

**Story Points:** 3

As the transfer workflow,
I want each instruction checked against its network, token, wallet, and authorization rules,
so that invalid or unauthorized transfers stop before compliance screening.

## Acceptance Criteria

- The validator checks both wallet addresses against the selected network's address format and rejects invalid or identical addresses when the token policy disallows self-transfers.
- The configured network and token contract are known, enabled, and consistent; unknown or mismatched configuration is rejected.
- The initiating principal is authorized to act for the source wallet, and the result is traceable to that principal and instruction.
- Applicable wallet eligibility, token restrictions, transfer limits, and available-balance checks are applied according to configured policy.
- Validation returns a structured pass or failure with a stable reason code; failure records the reason and leaves the instruction in a non-executable state.
- A failed validation does not initiate KYC, AML, sanctions screening, or a blockchain transaction.
- Automated tests cover invalid addresses, unsupported networks/tokens, unauthorized initiators, and valid instructions.

## Dependencies

- Story 2.1 must provide the persisted instruction and authenticated initiating principal.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-002, Phase 2, FR-002, AC-009
