# Story 1.6 — Apply a restrictive Content Security Policy

As a browser-client user,
I want browser-facing responses to enforce a tested Content Security Policy,
so that injected content has fewer opportunities to execute or exfiltrate data.

## Acceptance Criteria

- Browser-facing responses include the approved restrictive Content Security Policy; non-browser API behavior remains compatible.
- Directives cover the resources the client needs, including scripts, styles, images, connections, and frame ancestors.
- Inline scripts use approved nonces or hashes where required; policy exceptions are limited to documented dependencies.
- A report-only validation phase identifies legitimate violations before enforcement, and the enforced policy has no undocumented broad allowlists.
- CSP configuration is applied at the documented application or trusted web-serving layer and verified on representative responses.

## Open Decisions

- Identify browser-facing routes, deployed client origins, and required third-party resources before setting directive allowlists. The repository does not define a browser application or its production origins.

## Dependencies

- Stories 1.2–1.5 establish the shared security policy and finish changes to the common filter-chain configuration.

## References

- Epic — [Enterprise API Security and Access Control](../../epics/epic-01-security.md), Scope and Implementation Sequence, Step 6
