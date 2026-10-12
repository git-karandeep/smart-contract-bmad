# Story 1.8 — Document security operations and verify controls

As a security operator,
I want repeatable procedures for trust changes and security verification,
so that certificates and security controls remain manageable after release.

## Acceptance Criteria

- Operators can follow documented procedures to rotate and revoke certificates, update trusted client CAs, and respond to suspected credential or trust compromise.
- Dependency, security-configuration, and application scans run through the repository's documented checks, and their results are visible to reviewers.
- The operational guidance states how scan findings are handled and points to the repository's existing CI failure thresholds; it does not silently weaken them.
- The procedures distinguish local development fixtures from production trust and credentials.
- Certificate revocation and trust changes have an approved operational process, including how compromised certificates stop being accepted.

## Open Decisions

- Confirm the production ingress/secret-management operator and incident-response owner if those responsibilities are not established by deployment policy. This story documents the approved process; it does not introduce a custom identity provider or secret manager.

## Dependencies

- Story 1.1 establishes the transport trust model.
- Stories 1.3–1.7 establish the controls and events the procedures must cover.

## References

- Epic — [Enterprise API Security and Access Control](../../epics/epic-01-security.md), Out of Scope and Implementation Sequence, Step 8
- Security guidance — [Security baseline](../../security.md)
- CI — `.github/workflows/ci.yml`
