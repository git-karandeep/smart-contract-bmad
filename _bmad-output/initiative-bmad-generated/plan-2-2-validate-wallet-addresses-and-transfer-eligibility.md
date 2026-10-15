---
title: 'Validate wallet addresses and transfer eligibility'
type: 'feature'
ticket: 'Story-2.2'
created: '2026-10-15'
status: 'built'
baseline_revision: '89b6a687948767defd182f2f1ef2c9592e4fe60f'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/stories/epic-02-stories/Story-2.2.md'
  - '{project-root}/docs/epics/epic-02-wallet.md'
  - '{project-root}/_bmad-output/initiative-bmad-generated/plan-epic-02-wallet.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Transfer workflow validation currently depends on a policy interface whose default implementation rejects every instruction. The API validates EVM address syntax and source-wallet authorization at request time, but the workflow lacks an enabled policy to validate network/token configuration and other transfer eligibility before screening.

**Approach:** Implement Story 2.2 validation at the existing workflow boundary, with stable failure reasons, durable failed state, and tests proving rejected instructions cannot reach screening or submission. Use explicit configured policy for network/token eligibility and any balance, limit, or self-transfer rules.

## Boundaries & Constraints

**Always:** Fail closed when required policy or configuration is absent; preserve authenticated source-wallet entitlement checks, EVM address validation, exact decimal amounts, and the existing transfer lifecycle; prevent failed validation from reaching screening or chain submission.

**Never:** Invent supported network/token values, jurisdictional or token policy, balance/limit semantics, or production chain integrations; change API security or unrelated transfer stories.

**Decisions (2026-10-15):** Add a reusable fail-closed policy boundary and structural checks; defer network/token allowlists and balance/limit checks until approved values and data sources are available. Reject source-equals-destination transfers by default.

</frozen-after-approval>

## Code Map

- `modules/process-workers/src/main/java/ai/generalcontracts/startup/workflow/TransferValidationPolicy.java` — provider-neutral pass/fail policy contract; retain stable reason codes.
- `modules/process-workers/src/main/java/ai/generalcontracts/startup/workflow/TransferWorkerConfiguration.java` — current missing-policy bean rejects every transfer; keep default-deny behavior when no configured eligibility policy is available.
- `modules/process-workers/src/main/java/ai/generalcontracts/startup/workflow/TransferLifecycleJobHandlers.java:58-91` — invokes policy and persists `VALIDATION_FAILED` before returning `validationPassed=false`; preserve workflow boundary and status semantics.
- `modules/persistence-mongo/src/main/java/ai/generalcontracts/startup/transfer/TransferInstruction.java` — carries tenant, initiating principal, wallets, token, network, amount, status, and reason for policy evaluation.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/controller/TransferController.java` and `modules/contracts/src/main/java/ai/generalcontracts/startup/security/AssetEntitlementGuard.java` — creation-time caller/source authorization; do not bypass or broaden this check.
- `modules/process-workers/src/test/java/ai/generalcontracts/startup/workflow/TransferLifecycleJobHandlersTest.java` — existing fail-closed and no-submission regression; extend with policy outcomes.
- `modules/process-definitions/src/test/java/ai/generalcontracts/startup/workflow/TransferLifecycleBpmnTest.java` — assert validation remains before screening and rejection follows the BPMN failure path.

## Tasks & Acceptance

**Execution:**
- [ ] Add structural instruction validation at `TransferValidationPolicy.java` and compose it with an explicitly configured eligibility policy; reject malformed EVM addresses, same source/destination, invalid amount/network, and missing eligibility configuration with stable reason codes.
- [ ] Preserve creation-time source-wallet authorization in `TransferController.java`; do not claim balance or transfer-limit checks until their policy/data sources are approved.
- [ ] Extend `TransferLifecycleJobHandlersTest.java` and `TransferLifecycleBpmnTest.java` for pass/fail outcomes, durable failure reason, and no screening/submission on rejection.

**Acceptance Criteria:**
- Given a valid configured instruction, when workflow validation runs, then it returns a structured pass and only then permits screening to begin.
- Given invalid wallet/network/token data or an eligibility-policy rejection, when validation runs, then it persists a stable failure reason and no screening or blockchain side effect occurs.
- Given required policy/configuration is missing or an integration fails, when validation runs, then the instruction fails closed and cannot proceed.

## Implementation Notes

The repository establishes a fail-closed policy contract but does not define supported network/token configuration, a balance source, or transfer limits. Implement structural checks and the provider-neutral fail-closed boundary; do not use test fixture values as production policy.

Added structural checks composed with a fail-closed eligibility-policy port. Validation rejection persists the reason and exits before screening; configured eligibility implementations remain injectable. Source-wallet authorization remains at the existing API boundary.

No production network/token allowlist or authoritative balance/limit policy is present. The default eligibility policy therefore rejects transfers until an approved implementation is configured; this change does not enable production transfer execution by itself.

## Review Triage Log

- **false —** `TransferValidationPolicy` permits non-address token identifiers because the request contract accepts a token identifier as well as a contract address. With no configured eligibility policy, the default denies all instructions; when configured, that policy owns recognition of supported identifiers. The finding assumes all token identifiers must be EVM addresses, which is not specified by Story 2.2.
- **medium — patch —** `TransferWorkerConfiguration.transferValidationPolicy` was unconditional, so a deployment supplying the existing `TransferValidationPolicy` extension bean would create ambiguous injection into `TransferLifecycleJobHandlers`. Added `@ConditionalOnMissingBean(TransferValidationPolicy.class)` so the composed default is registered only when no custom policy exists.

## Verification

**Commands:**
- `.\gradlew.bat :modules:process-workers:test :modules:process-definitions:test --no-daemon` — passed after review fix; worker and BPMN validation tests passed.
- `git diff --check` — passed; no whitespace errors.
