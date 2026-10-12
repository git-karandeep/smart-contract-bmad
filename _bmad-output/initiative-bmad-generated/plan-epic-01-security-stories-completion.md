---
title: 'Complete Epic 01 security stories'
type: 'feature'
ticket: 'Story-1.1-to-Story-1.10'
created: '2026-10-11'
status: 'in-review'
baseline_revision: '14c880e430151e90214f085a14b72bfc2e751727'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/stories/epic-01-stories/'
  - '{project-root}/_bmad-output/initiative-bmad-generated/plan-epic-01-security-implementation.md'
  - '{project-root}/docs/security.md'
  - '{project-root}/deployment/SECURITY.md'
  - '{project-root}/modules/contracts/src/main/java/ai/generalcontracts/startup/config/SecurityConfiguration.java'
  - '{project-root}/modules/contracts/src/test/java/ai/generalcontracts/startup/SecurityConfigurationTest.java'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Most of Epic 01's API security boundary is implemented, but acceptance remains weakly verified: the effective CSP has duplicate directives and the full certificate-protected path depends on opt-in external smoke fixtures.

**Approach:** Reconcile all ten stories with the existing implementation and approved decisions, correct verified gaps, and document executable verification without claiming unavailable production fixtures were exercised.

## Boundaries & Constraints

**Always:** Preserve existing user changes; use verified JWT authorities and TLS-validated certificate identity; fail closed; prevent cross-tenant disclosure; retain CI scan thresholds.

**Never:** Invent identity, role, tenant, browser-origin, ingress, or revocation policy. Do not modify story requirements, unrelated modules, or the pre-existing `.idea/dataSources.local.xml` and `modules/chain-listener/build.gradle` changes.

**Recorded decisions:** Spring Boot terminates TLS and requests client certificates; JWT validates issuer, audience, time, subject, and `typ` (`at+jwt`/`JWT`). Only health probes are public; verified scopes gate other routes. MongoDB supplies asset entitlements. No browser-origin allowlist or per-leaf CRL/OCSP is defined. Rotate trust via mounted material and restart. See the prior Epic 01 plan and security guide.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Behavior | Error Handling |
|---|---|---|---|
| Health probe | GET health without identity | Reachable | No identity details |
| Protected API | Invalid JWT, certificate, scope, or entitlement | Deny before action | Generic response; safe audit |
| API response | Inspect CSP and security headers | Exact restrictive policy | Test fails on drift |

</frozen-after-approval>

## Code Map

- `modules/contracts/src/main/java/ai/generalcontracts/startup/config/SecurityConfiguration.java`, `util/JWTDecode.java`, and `security/ClientCertificateFilter.java` — central route policy, verified JWT authority mapping, TLS certificate gate.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/security/AssetEntitlementAuthorizer.java`, `AssetEntitlementGuard.java`, and `observability/SecurityAuditLogger.java` — trusted resource checks and sanitized audit decisions.
- `modules/contracts/src/test/java/ai/generalcontracts/startup/` — extend existing route, JWT, entitlement, profile, and audit coverage.
- `tests/e2e-smoke.sh`, `docs/security.md`, `deployment/SECURITY.md`, `tests/README.md`, `.github/workflows/ci.yml` — existing prerequisites, operations, and scan gates.
- `_bmad-output/initiative-bmad-generated/plan-epic-01-security-implementation.md` — continuity, not proof each criterion passes.

## Tasks & Acceptance

**Execution:**
- [x] `modules/contracts/src/main/java/ai/generalcontracts/startup/config/SecurityConfiguration.java` and `modules/contracts/src/test/java/ai/generalcontracts/startup/SecurityConfigurationTest.java` — remove conflicting CSP directives; assert the complete policy and headers.
- [x] `modules/contracts/src/test/java/ai/generalcontracts/startup/config/JwtPolicyValidatorTest.java`, `modules/contracts/src/test/java/ai/generalcontracts/startup/security/AssetEntitlementAuthorizerTest.java`, `modules/contracts/src/test/java/ai/generalcontracts/startup/SecurityConfigurationTest.java`, and `tests/e2e-smoke.sh` — reconcile all stories with existing unit/API/integration and opt-in transport checks; cover credential, certificate, scope, tenant, audit, and route failures where fixtures allow.
- [x] `docs/security.md`, `deployment/SECURITY.md`, and `tests/README.md` — align TLS boundary, revocation limits, CSP, prerequisites, and runnable checks.
- [x] `modules/contracts/src/main/java/ai/generalcontracts/startup/config/SecurityConfiguration.java` and `modules/contracts/src/test/java/ai/generalcontracts/startup/` — consolidate only evidenced duplication; retain unrelated behavior and tests.

**Acceptance Criteria:**
- Given any route, when central policy evaluates it, then only approved health probes are public and others follow verified-scope rules or deny-by-default.
- Given invalid protected-request identity, certificate, permission, or entitlement, when the request reaches the security boundary, then deny before action without header-based identity or cross-tenant disclosure.
- Given an API response or audit event, when headers or logs are inspected, then CSP is restrictive and logging contains only bounded, sanitized correlation context.
- Given documented checks and prerequisites, when they run, then failures fail the check, scan thresholds remain unchanged, and unavailable fixtures are disclosed.

## Implementation Notes

Removed duplicate and conflicting CSP directives; the exact restrictive API policy is asserted on health and denied responses and by the smoke script. Updated operations/testing docs to record exact policy, fixture gates, and verification commands. Existing JWT, route, entitlement, and audit controls were retained. Gradle contracts unit/API/integration suites passed (56 unit tests; API/integration tasks successful); Solidity compile/tests (2)/lint and smoke-script syntax checks passed. Docker smoke could not run because Docker engine is unavailable. External protected TLS checks and CI scans were not run; required external fixtures were unavailable.

## Plan Change Log

## Review Triage Log

## Design Notes

This is one dependency-ordered security boundary, not ten independent deliverables. Reuse the prior implementation and fix only evidence-backed gaps. TLS validates certificate chains at the connector. Protected external tests remain opt-in because fixtures are absent.

## Verification

**Commands:**
- `.\gradlew.bat :modules:contracts:test :modules:contracts:apiTest :modules:contracts:integrationTest --no-daemon` — all suites pass.
- `npm run contracts:compile; npm run contracts:test; npm run contracts:lint` — compile, contract tests, and lint pass.
- `bash tests/e2e-smoke.sh` — local smoke passes with prerequisites; protected checks require documented fixtures.
- CI scans retain thresholds; disclose scans not run. `git diff --check` has no errors.
