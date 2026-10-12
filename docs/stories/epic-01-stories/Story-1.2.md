# Story 1.2 — Centralize request security and deny by default

As a platform engineer,
I want a central Spring Security policy for every application route,
so that protected endpoints cannot bypass authentication through controller-level omissions.

## Acceptance Criteria

- An approved route inventory identifies every current application route as public, authenticated, or mTLS-protected, including health, CSRF, and actuator routes.
- Every application route is denied unless the central policy explicitly marks it public or assigns an authorization rule.
- Health and other approved public endpoints remain reachable without authentication; no unlisted route is implicitly public.
- Security filter ordering is explicit and verified for the configured authentication and authorization mechanisms.
- Session, CSRF, and state-changing request behavior match the documented API and browser-client model.
- Responses include the approved security-header baseline for the application's API and browser use; header policy is tested independently of CSP.
- An unknown route or newly added controller does not become accessible without an explicit policy.
- Automated security tests prove protected routes reject unauthenticated callers and verify filter ordering and route-policy coverage.

## Open Decisions

- Approve the route inventory and public-route allowlist before implementation. The epic does not identify which non-health endpoints, if any, are intentionally public.
- Approve the non-CSP security-header baseline for the deployed application type; do not assume browser-only headers apply identically to API clients.

## Dependencies

- Story 1.1 establishes the trusted transport boundary.

## References

- Epic — [Enterprise API Security and Access Control](../../epics/epic-01-security.md), Architectural Rules and Implementation Sequence, Step 2
- Existing implementation — `modules/contracts/src/main/java/ai/generalcontracts/startup/config/SecurityConfiguration.java`
- Existing security test — `modules/contracts/src/test/java/ai/generalcontracts/startup/SecurityConfigurationTest.java`
