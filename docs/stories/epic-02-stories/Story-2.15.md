# Story 2.15 — Audit transfer decisions and monitor workflow health

As a compliance or operations user,
I want material transfer decisions and system failures traceable by instruction,
so that investigations and operational response have reliable evidence.

## Acceptance Criteria

- Material state transitions, validation results, screening request/result references, compliance decisions, manual-review actions, transaction hashes, reconciliation results, and final outcomes are recorded with instruction and correlation identifiers.
- Audit records are append-only or otherwise protected from unauthorized alteration and include actor/source and timestamp where applicable.
- Logs, metrics, and traces connect API requests, Camunda instances, provider messages, Kafka events, and blockchain transactions without exposing private keys or unnecessary identity data.
- Alerts cover at least screening timeouts/provider failures, transaction unknown/reverted outcomes, reconciliation mismatches, and workflow failures requiring operations.
- Restricted manual-review and administrative actions are role controlled and auditable.
- Operational dashboards and alert/runbook guidance explain actionable states without treating submission acknowledgment as transfer success.
- Tests or documented checks verify correlation, sensitive-data redaction, audit durability, and alert triggering for representative failures.

## Open Decisions

- Confirm audit retention/access policy, required event fields, monitoring/alerting destination, and the operational owners and escalation thresholds.

## Dependencies

- Stories 2.1, 2.3, 2.7, 2.8, 2.10, 2.12, and 2.13 must expose the lifecycle and decision signals to be audited and monitored.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-010, FR-011, Non-Functional Requirements, Security Requirements, AC-015
