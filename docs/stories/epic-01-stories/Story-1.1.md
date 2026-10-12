# Story 1.1 — Establish the transport security boundary

As a platform engineer,
I want HTTPS and mTLS enforced at the trusted ingress for designated routes,
so that requests reach the application over an authenticated and protected transport.

## Acceptance Criteria

- TLS terminates only at the documented trusted boundary; application traffic from that boundary is protected according to the deployment design.
- Server certificates are validated by clients, and every hop carrying protected requests is encrypted or explicitly identified as a trusted local-only exception.
- Routes designated for mTLS reject requests without a valid trusted client certificate and accept a valid certificate.
- Certificate identity is accepted only from the TLS connector or a documented trusted proxy; client-supplied identity headers cannot impersonate a certificate.
- Certificate chain, validity period, and trust are checked. Certificate failure does not expose certificate or trust-store details to callers.
- The route-to-transport policy and TLS termination point are recorded in the security documentation.

## Open Decisions

- Identify the deployment TLS termination point, the routes that require mTLS, the trusted client CA source, whether TLS is re-established from ingress to application, and whether the application or ingress performs client-certificate authentication. Resolve these before implementation; do not infer the production topology from local Compose.

## Dependencies

- None. This establishes the transport boundary used by the remaining security stories.

## References

- Epic — [Enterprise API Security and Access Control](../../epics/epic-01-security.md), Security Architecture and Implementation Sequence, Step 1
- Security guidance — [Security baseline](../../security.md), Transport and access
