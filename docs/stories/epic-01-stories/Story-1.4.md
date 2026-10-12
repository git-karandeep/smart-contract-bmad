# Story 1.4 — Enforce role- and scope-based access

As a platform owner,
I want endpoint permissions enforced from verified roles and scopes,
so that authenticated callers receive only the access granted to them.

## Acceptance Criteria

- Protected operations have an explicit mapping to required roles or scopes.
- Requests without a required permission are denied consistently with HTTP 403, while permitted requests continue.
- Request-level and method-level rules cannot be bypassed by invoking an operation through a different route.
- Public routes are explicitly designated; all other operations follow least privilege.
- Caller-provided roles, scopes, or headers cannot elevate the authorities established by Story 1.3.

## Open Decisions

- Approve the role/scope vocabulary and its mapping to protected operations before policy implementation; the epic does not define either.

## Dependencies

- Story 1.3 supplies the verified principal and authorities.

## References

- Epic — [Enterprise API Security and Access Control](../../epics/epic-01-security.md), Architectural Rules and Implementation Sequence, Step 4
