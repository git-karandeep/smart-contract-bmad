# Story 1.7 — Standardize security failures and audit events

As an operations engineer,
I want authentication and authorization failures to be consistent and auditable,
so that security decisions can be investigated without exposing sensitive data.

## Acceptance Criteria

- Missing or invalid authentication returns HTTP 401; an authenticated caller without permission receives HTTP 403.
- Error responses do not expose credentials, tokens, certificates, internal policy details, or sensitive asset-existence information.
- Security-relevant authentication outcomes, authorization denials, and approved sensitive access decisions are recorded with the available correlation identifier and minimum decision context.
- Audit and application logs exclude passwords, private keys, bearer tokens, raw credentials, and sensitive request data.
- Log values originating from requests are bounded and sanitized so control characters cannot forge additional audit entries.

## Dependencies

- Stories 1.3–1.6 establish the authentication, authorization, and response-policy outcomes to report.

## References

- Epic — [Enterprise API Security and Access Control](../../epics/epic-01-security.md), Implementation Sequence, Step 7
- Observability — [Security baseline](../../security.md), Credentials and signing keys; Transport and access
