# Story 2.8 — Aggregate compliance decisions and enforce timeouts

As the compliance decision service,
I want all mandatory screening outcomes evaluated under one fail-closed policy,
so that only fully approved transfers can proceed.

## Acceptance Criteria

- The workflow waits for every mandatory KYC, AML, and sanctions decision, independent of arrival order, before evaluating the complete result set.
- The transfer is approved only when every mandatory result is valid, current, and `APPROVED`.
- Any rejection follows the configured rejection/hold policy; `REVIEW_REQUIRED` enters a manual-review path; sanctions outcomes requiring restriction remain held under the applicable policy.
- Provider errors trigger only bounded retries or an operational hold; unresolved results and timeouts never become approvals.
- Timeout, error, rejection, and review outcomes persist their reason and transition the instruction to the corresponding non-executable state.
- Duplicate and late results do not change an already decided terminal or held state without an authorized, audited action.
- Tests cover all-arrival orders, mixed outcomes, missing results, timeout, provider error, and late-result behavior.

## Open Decisions

- Approve the decision precedence and configured timeout, retry/backoff, escalation, manual-review, and sanctions-hold policies. These policies must not weaken mandatory legal controls.

## Dependencies

- Story 2.7 must persist and correlate screening decisions.

## References

- Epic — [Wallet-Based Token Movement](../../epics/epic-02-wallet.md), BR-003–BR-006, Phase 5, FR-006, FR-007, AC-002–AC-008
