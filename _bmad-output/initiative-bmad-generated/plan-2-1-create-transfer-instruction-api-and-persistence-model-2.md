---
title: 'Reject zero-value transfer instructions at the API boundary'
type: 'bugfix'
ticket: 'Story-2.1'
created: '2026-10-15'
status: 'built'
baseline_revision: '2273b457974a812a4831eb4709bb1088a8a70967'
route: 'oneshot'
route_source: 'auto'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/stories/epic-02-stories/Story-2.1.md'
  - '{project-root}/_bmad-output/initiative-bmad-generated/plan-2-1-create-transfer-instruction-api-and-persistence-model.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Zero-valued amounts match the API request's numeric pattern but fail later in the lifecycle service with `IllegalArgumentException`, producing a server error instead of a clear client validation response.

**Approach:** Enforce strictly positive decimal amounts at request validation and add route-level regression coverage proving zero values are rejected before persistence or workflow launch.

## Boundaries & Constraints

**Always:** Preserve exact decimal handling, request pattern/precision bounds, the transfer API contract for valid requests, and all pre-existing staged and unstaged work.

**Never:** Change lifecycle semantics, security configuration, amount limits, or unrelated stories/modules.

</frozen-after-approval>

## Implementation Notes

The existing `TransferRequest.amount` pattern admits `0` and `0.0`, while `TransferLifecycleService.canonicalAmount` rejects them. A Jakarta `@DecimalMin` constraint on the request field should fail through existing `@Valid` handling before the lifecycle service is invoked.

Added a strict-positive decimal constraint to `TransferRequest.amount`. Extended `TransferRouteSecurityTest` to exercise scientific notation and zero values (`0`, `0.0`) through the authenticated API and verify no lifecycle persistence occurs. Tagged that route-level validation test for the dedicated `apiTest` task so the regression is included in focused verification.

## Verification

**Commands:**
- `.\gradlew.bat :modules:contracts:apiTest :modules:domain-service:test --no-daemon` — passed; API regression and lifecycle service tests passed.
- `git diff --check` — expected: no whitespace errors.
