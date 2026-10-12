---
title: 'Implement Epic 1 API security boundary'
type: 'feature'
ticket: 'Story-1.1-to-Story-1.10'
created: '2026-10-09'
status: 'built'
baseline_revision: 'b6d943e9ff8cdf7e29e76d8c43830f5f60ef8bad'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 1
context:
  - '{project-root}/docs/epics/epic-01-security.md'
  - '{project-root}/docs/stories/epic-01-stories/'
  - '{project-root}/docs/security.md'
  - '{project-root}/modules/contracts/src/main/java/ai/generalcontracts/startup/config/SecurityConfiguration.java'
  - '{project-root}/modules/contracts/src/test/java/ai/generalcontracts/startup/SecurityConfigurationTest.java'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Epic 1's stories define a complete request-security boundary, but the current application permits all non-health routes and has no configured authentication, resource authorization, CSP, or mTLS deployment boundary.

**Approach:** Implement the approved security policy in the existing Spring Boot contracts application, with tests at unit, integration, and end-to-end boundaries. Keep deployment-specific trust, identity, entitlement, and browser policies configurable and document the operational procedures.

## Boundaries & Constraints

**Always:** Preserve pre-existing staged and unstaged user changes. Do not claim production security from local-only fixtures. Fail closed for protected routes, validate identity before authorization, and keep credentials, tokens, certificates, and sensitive asset details out of responses and logs.

**Never:** Add a custom identity provider or cryptographic protocol, invent role/tenant policy, trust forwarded identity headers from untrusted proxies, or modify Epic 1 story requirements. Do not change unrelated modules or business behavior.

**Decisions (2026-10-09):**
- Use Spring OAuth2 Resource Server JWT validation with issuer and audience supplied by environment.
- Accept only JWT `typ` values `at+jwt` and `JWT`; reject missing and unsupported token types.
- Terminate production TLS in Spring Boot; request client certificates optionally at handshake, require a trusted certificate on protected API routes, and leave health probes accessible without one.
- Load the production CA truststore from externally mounted deployment secrets. Rotate/revoke by updating trust material and rolling/restarting the application.
- Require both a valid JWT and trusted client certificate, independently validated; do not require a JWT `cnf` thumbprint binding.
- Publish only health/readiness/liveness anonymously. Require `ORDER_READ` for `GET /orders`, `ORDER_WRITE` for `POST /orders`, and `CHAIN_STATUS_READ` for `GET /api/chain/status`; require `OPS_READ` for other Actuator routes and deny all remaining routes. Map these names from verified OAuth2 `scope` claims. Remove the unused CSRF endpoint for the stateless bearer-token API.
- Create a minimal MongoDB entitlement registry keyed by verified principal, tenant, asset, and permission; use synthetic test resources until business asset endpoints exist.
- Apply a restrictive API CSP (`default-src 'none'`, `base-uri 'none'`, `frame-ancestors 'none'`) and standard Spring security headers. Defer browser-specific allowlists until a client is defined.

</frozen-after-approval>

## Code Map

- `modules/contracts/src/main/java/ai/generalcontracts/startup/config/SecurityConfiguration.java` — current filter chain permits every request except explicit health matcher and disables HTTP Basic; central policy and CSRF are configured here.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/controller/` — current order, chain-status, and CSRF routes; build an explicit route inventory from controllers and Actuator exposure.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/observability/RequestCorrelationFilter.java` — existing bounded request/correlation IDs; reuse for audit context, not as authentication.
- `modules/contracts/src/test/java/ai/generalcontracts/startup/SecurityConfigurationTest.java` and `modules/contracts/build.gradle.kts` — existing CSRF test and unit/API/integration test tasks; extend rather than create parallel harnesses.
- `modules/contracts/src/main/resources/application*.yaml` — profile configuration and production validation; security provider, TLS, and CSP settings must align with it.
- `docs/security.md`, `deployment/`, `.github/workflows/ci.yml` — security operations, TLS termination, and scan gates; no ingress/Helm configuration currently exists. Preserve existing scan thresholds.
- Do not modify the staged Epic renames or unrelated IDE, Story1, and chain-listener worktree changes.

## Tasks & Acceptance

**Execution:**
- [x] `modules/contracts/src/main/java/ai/generalcontracts/startup/config/SecurityConfiguration.java`, `modules/contracts/src/main/resources/application*.yaml`, and `modules/contracts/src/main/java/ai/generalcontracts/startup/controller/CsrfTokenController.java` — implement deny-by-default JWT + optional-handshake/required-route mTLS, the approved scope map, stateless CSRF policy, API CSP/headers, and remove the obsolete CSRF endpoint.
- [x] `modules/contracts/src/main/java/ai/generalcontracts/startup/` — implement validated JWT authority mapping and the MongoDB principal/tenant/asset/permission entitlement registry; enforce checks on resource operations without trusting request-supplied claims.
- [x] `modules/contracts/src/test/java/ai/generalcontracts/startup/` — cover route inventory, filter order, accepted and rejected credentials, role/tenant denials, CSRF, CSP/headers, and sanitized auditable decisions.
- [x] `modules/contracts/src/main/resources/application-prod.yaml`, `docs/security.md`, and deployment documentation — configure secret-mounted TLS key/trust material, trusted client certificates, revocation/rotation rollout steps, and route-to-transport policy; leave local/test transports unchanged.
- [x] `docs/security.md` and `.github/workflows/ci.yml` — align authentication and operational guidance with implemented behavior; verify dependency, configuration, and application scan results use existing failure thresholds.
- [x] `modules/contracts/src/test/` and `tests/` — add the closing integration/E2E path for certificate, credential, authorization, CSP, denial, and audit behavior; keep tests runnable with explicit external prerequisites.
- [x] `modules/contracts/src/main/java/ai/generalcontracts/startup/` — perform the post-feature refactor sweep and retain security coverage.

**Acceptance Criteria:**
- Given an unlisted route or missing/invalid identity, when a request reaches the application, then it is denied by the approved policy and reveals no internal security detail.
- Given an authenticated principal, when it requests an operation outside its verified role, scope, tenant, or asset entitlement, then the request is denied without revealing another tenant's asset existence.
- Given approved certificate and caller credentials, when a designated protected request is made through the documented transport boundary, then only the configured trusted identity and authorization policy can allow it.
- Given browser-facing responses, when inspected and exercised by the documented client, then approved CSP and security headers are present without breaking required behavior.
- Given security failures and sensitive allowed decisions, when recorded, then correlation context is available and secret-bearing values are excluded.
- Given the documented unit, integration, E2E, and security-scan commands, when run in their stated environments, then covered failures fail the corresponding check.

## Implementation Notes

The repository's security guide describes HTTP Basic, but the active filter chain disables it and permits all non-health requests. Implement the approved JWT contract and correct the documentation rather than preserving that inaccurate description.

The external HTTPS/JWT/client-certificate smoke path is opt-in and requires a test issuer plus trusted and untrusted certificate fixtures; it was not exercised without those credentials.
CI dependency, configuration, source, and image scans retain their existing HIGH/CRITICAL failure thresholds; those scans were not run locally.

## Plan Change Log

- 2026-10-09: User resolved the JWT token-type choice as `at+jwt` or `JWT`, with missing and unsupported types rejected; implementation and validation now include that decision.

## Review Triage Log

- false — The E2E authorization headers are wired to `APP_SECURITY_E2E_BEARER_TOKEN`; the displayed `******` masks bearer values. The remaining hard-coded bearer header is the deliberate invalid-token case. Raw source checks confirmed the three protected cases reference the configured variable.
- medium — `SecurityConfiguration.jwtValidators` initially accepted valid issuer/audience/time/subject claims without checking `typ`. User resolved the policy to accept only `at+jwt` or `JWT`; the validator now enforces this and tests accept both while rejecting missing/unsupported types.
- medium — The post-feature refactor sweep was still incomplete. Consolidated duplicated servlet path/context fallback into `RequestPathResolver`, added direct resolver tests, and reran the focused security suites.

## Design Notes

The first demonstrable path is one protected order request from the selected trusted transport boundary through credential validation, an explicit route policy, and a testable allow/deny result. The central `SecurityFilterChain` is a shared seam; policy changes must be sequenced rather than independently built. Asset authorization and CSP remain separate concerns after the principal and route policy are stable. The post-feature sweep centralized servlet-path resolution shared by the certificate filter and audit routing before the closing end-to-end suite.

## Verification

**Commands:**
- `.\gradlew.bat :modules:contracts:test :modules:contracts:apiTest :modules:contracts:integrationTest --no-daemon --rerun-tasks` — all unit, API, and integration tests pass.
- `bash tests/e2e-smoke.sh` — local Docker smoke suite passes when run with Git Bash; external protected TLS checks require the documented test issuer and certificate fixtures and were not exercised.
- `git diff --check` — no whitespace errors; existing unrelated changes remain untouched.
