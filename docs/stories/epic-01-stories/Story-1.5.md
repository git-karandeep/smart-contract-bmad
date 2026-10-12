
# Story 1.5 — Enforce asset and tenant entitlements

As an asset owner,
I want each asset operation authorized against trusted entitlements,
so that an authenticated caller cannot access another owner's or tenant's assets.

## Acceptance Criteria

- Each protected asset operation resolves the target asset and checks the authenticated principal's permission for that asset.
- Cross-tenant and cross-asset access is denied even when the caller has a valid identity and a general API role.
- Entitlements are obtained from an approved trusted source; request parameters and unverified token claims cannot grant access.
- Authorization is rechecked when entitlement changes may occur during a workflow, before the protected action is committed.
- Denials do not disclose whether another tenant's asset exists.

## Open Decisions

- Identify the authoritative entitlement source, tenant and ownership identifiers, and which workflows require authorization revalidation. Resolve these before implementation.

## Dependencies

- Story 1.4 establishes the verified role and scope policy used alongside resource-level checks.

## References

- Epic — [Enterprise API Security and Access Control](../../epics/epic-01-security.md), Architectural Rules and Implementation Sequence, Step 5
- Data model — [Data model](../../data-model.md)
