# Story 1.3 — Authenticate callers and validate credentials

As an API owner,
I want caller credentials validated before a principal enters the security context,
so that authorization decisions use verified identities rather than client claims.

## Acceptance Criteria

- Missing, malformed, expired, or invalid credentials are rejected before protected application logic runs.
- A protected route is inaccessible without a credential accepted by the approved production authentication mechanism; development-only fixtures cannot grant production access.
- When JWT authentication is selected, the token signature, issuer, audience, expiration, not-before time, required claims, and token type are validated.
- The authenticated principal and authorities are derived only from validated credentials or the trusted client identity established by Story 1.1.
- Unverified token claims and client-supplied identity or role headers cannot establish identity or authority.
- Credential validation failures do not reveal tokens, signing material, or internal validation details.
- Automated tests cover accepted credentials and missing, malformed, expired, and invalid credentials for the selected mechanism.

## Open Decisions

- Resolved for this implementation: use OAuth2 Resource Server JWT with environment-configured issuer and audience; accept only `at+jwt` and `JWT` token types, and obtain signing keys through the issuer's OIDC/JWK discovery endpoint. No identity provider or custom authentication protocol is assumed.

## Source Conflict

- `docs/security.md` describes HTTP Basic as the current application authentication boundary, while `SecurityConfiguration.java` disables HTTP Basic and permits all requests. Resolve this discrepancy when selecting the production mechanism, and update the security documentation as part of implementation.

## Dependencies

- Story 1.2 establishes the central filter chain and route policy.

## References

- Epic — [Enterprise API Security and Access Control](../../epics/epic-01-security.md), Scope and Implementation Sequence, Steps 2–3
- Security guidance — [Security baseline](../../security.md), Transport and access
