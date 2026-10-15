# Story 2.15 — Implement audit trail, monitoring, and alerts

**Story Points:** 5

As a compliance operations team,
I want correlated audit records and operational signals for transfer processing,
so that decisions, failures, and workflow health can be investigated without exposing secrets.

## Acceptance Criteria

- Material instruction transitions, screening requests and decisions, transaction references, reconciliation outcomes, and terminal results are traceable by instruction ID.
- Audit records preserve the actor/provider, timestamp, correlation identifiers, policy/version context, and outcome needed to explain each decision.
- Audit records are append-only or otherwise protected from unauthorized alteration, and duplicate delivery does not create misleading duplicate decisions.
- Operational signals expose configured workflow failures, screening timeouts, stuck transfers, unknown transactions, and reconciliation mismatches.
- Logs and audit payloads minimize identity data and never expose private keys, credentials, or signing material.
- Access to sensitive audit and administrative data follows the existing authentication, authorization, and audit controls.
- Automated tests verify event correlation, sensitive-data redaction, and signals for representative failure paths.

## Open Decisions

- Confirm audit retention, access policy, append-only storage guarantee, alert destinations, and operational service-level thresholds.

## Dependencies

- Stories 2.3, 2.7, 2.8, 2.12, and 2.13 must provide the lifecycle, decision, and transaction events to observe.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-010, Non-Functional Requirements, Security Requirements, FR-011, AC-015
