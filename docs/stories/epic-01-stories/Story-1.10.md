# Story 1.10 — Verify the complete security request path

As a release owner,
I want an end-to-end security suite for representative requests,
so that transport, identity, authorization, browser policy, and failure handling work together.

## Acceptance Criteria

- Automated unit and integration tests cover the security rules implemented by the preceding stories; the closing suite verifies their end-to-end interaction.
- The suite verifies accepted and rejected client certificates for routes designated for mTLS.
- The suite verifies valid and invalid authentication, including JWT claims when JWT is the approved mechanism.
- The suite verifies allowed and denied roles/scopes and asset/tenant entitlements, including cross-tenant access.
- The suite verifies CSP headers on browser-facing responses and confirms that approved browser behavior continues to work.
- Authentication and authorization failures have the expected status and safe response, and produce auditable, non-sensitive records.
- The suite verifies security-filter ordering and confirms every current route has an explicit public, authentication, or authorization policy.
- The documented security checks run in their supported environment and fail when a covered security assertion fails.

## Open Decisions

- Test fixtures and expected identities, roles, entitlements, certificates, and browser origins must follow the decisions recorded while completing Stories 1.1 and 1.3–1.6; this suite must not invent alternate policy.

## Dependencies

- Story 1.9 must be complete so the closing suite exercises the final security boundary.

## References

- Epic — [Enterprise API Security and Access Control](../../epics/epic-01-security.md), Implementation Sequence, Step 8 and Definition of Done
- Test organization — [Test layers](../../../tests/README.md)
