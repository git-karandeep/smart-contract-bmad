---
title: 'Fix contracts test compilation'
type: 'bugfix'
ticket: ''
created: '2026-10-09'
status: 'built'
baseline_revision: 'b6d943e9ff8cdf7e29e76d8c43830f5f60ef8bad'
route: 'oneshot'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: ['quick']
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The contracts test source no longer compiles because `JwtPolicyValidatorTest` calls `SecurityConfiguration.jwtValidators`, which was extracted to `JWTDecode`. The new helper is also constructor-injected by `SecurityConfiguration` but is not registered as a Spring bean.

**Approach:** Update the validator test to exercise the extracted helper and register `JWTDecode` for dependency injection. Preserve the existing JWT validation behavior and all unrelated staged/unstaged work.

</frozen-after-approval>

## Implementation Notes

Repository-wide `test` fails at `:modules:contracts:compileTestJava` with ten unresolved references to `SecurityConfiguration.jwtValidators`. The production security configuration calls the extracted `JWTDecode.jwtValidators` and constructor-injects `JWTDecode`.

Registered `JWTDecode` as a Spring component and updated `JwtPolicyValidatorTest` to invoke the extracted validator. The combined repository unit, contracts API, and integration suites now pass.

Review reported no findings. No commit was created because the worktree contains unrelated pre-existing staged changes and the JWT helper itself had staged content before this fix.

## Verification

- `.\gradlew.bat test --no-daemon` — repository unit tests compile and pass.
- `.\gradlew.bat :modules:contracts:apiTest :modules:contracts:integrationTest --no-daemon` — contracts API and integration tests pass.
