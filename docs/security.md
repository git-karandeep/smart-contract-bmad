# Application security boundary

This repository provides a local-development foundation, not a production identity provider, key-custody service, or complete deployment platform. Production access is deny-by-default and requires both a valid OAuth2 access token and a trusted TLS client certificate on protected routes.

## Authentication and route policy

The contracts application uses Spring OAuth2 Resource Server JWT validation. Configure `APP_SECURITY_JWT_ISSUER` and `APP_SECURITY_JWT_AUDIENCE` in the production secret/configuration service. The issuer's standard OIDC/JWK discovery endpoint supplies signing keys; the resource server validates the signature, issuer, audience, expiration, and not-before time, requires a non-empty `sub` claim, and accepts only `typ` values `at+jwt` or `JWT`. Missing or unsupported token types are rejected. Keep issuer/JWK availability and signing-key rotation in the identity provider's operational controls. The application does not provision users or issue tokens. Missing issuer or audience configuration makes token validation fail closed; the production profile refuses to start without both values.

Authorities come only from the verified token's OAuth2 `scope` claim:

| Request | Required verified scope |
| --- | --- |
| `GET /orders` | `ORDER_READ` |
| `POST /orders` | `ORDER_WRITE` |
| `GET /api/chain/status` | `CHAIN_STATUS_READ` |
| Any non-health `/actuator/**` endpoint | `OPS_READ` |
| `GET /actuator/health`, `/actuator/health/**` | Public liveness/readiness probes |
| Any other route or method, including the framework `/error` route | Denied |

No identity, scope, or role is taken from request headers. Browser/API access is stateless: sessions and CSRF are disabled, and the obsolete `/api/security/csrf` endpoint has been removed. Protected requests without a valid token return a generic `401`; authenticated requests without the required scope or certificate receive a generic `403`.

Asset operations must use `AssetEntitlementGuard` before reading or changing a resource. It compares the requested tenant with the verified JWT `tenant_id` claim and queries the MongoDB `asset_entitlements` registry by verified `sub`, tenant, asset, and `READ`/`WRITE` permission. A tenant mismatch is denied before lookup, so another tenant's asset existence is not disclosed. Business asset routes do not yet exist; tests exercise this guard with synthetic resources. Do not treat a scope as a substitute for an asset entitlement.

## TLS and mTLS deployment boundary

In production TLS terminates in the Spring Boot embedded server on the configured application port (`8443` by default); this repository does not define an ingress or proxy. Every protected client connects directly to that HTTPS listener. If a future deployment adds a proxy, it must re-establish TLS to the application, and the proxy must not replace the TLS connector's certificate identity with forwarded headers.

The `prod` profile loads a PKCS#12 server keystore and client truststore from externally mounted files. The default mount paths are `/run/secrets/server-keystore.p12` and `/run/secrets/client-truststore.p12`; passwords and path overrides are supplied through secret-backed environment variables:

- `SERVER_SSL_KEY_STORE_PATH`, `SERVER_SSL_KEY_STORE_PASSWORD`
- `SERVER_SSL_TRUST_STORE_PATH`, `SERVER_SSL_TRUST_STORE_PASSWORD`
- `SERVER_SSL_ENABLED=true`, `SERVER_SSL_CLIENT_AUTH=want`

The TLS connector requests client certificates (`want`) so public health probes can connect without one. JSSE validates presented certificate chains against the mounted truststore and enforces validity; Spring Security then requires the TLS connector's verified certificate attribute for protected API and non-health Actuator routes. A missing certificate on an otherwise authenticated protected request receives `403`; an untrusted or invalid certificate is rejected by the TLS handshake. A certificate passed in an HTTP header is never accepted. Health probes should omit client certificates.

The `local` and `test` profiles retain their existing HTTP transport and do not load production trust material. Local mode has no default username/password. Protected routes remain inaccessible unless a JWT and a TLS-established client certificate are provided; local HTTP mode is not a way to exercise or bypass the production transport boundary. MongoDB production TLS and hostname verification remain enabled.

## Headers, CSP, and audit

Responses use exactly `Content-Security-Policy: default-src 'none'; base-uri 'none'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, and `Permissions-Policy: camera=(), microphone=(), geolocation=()`. No script, style, image, or frame source is allowed by CSP. There is no browser client or documented third-party resource allowlist; add a narrower reviewed policy before introducing one. The API response policy is tested for exact equality to prevent conflicting or unreviewed directives.

Security decision logs record the fixed route/permission category and allow/deny result with the validated correlation identifier from `RequestCorrelationFilter`. They do not log principal/tenant/asset identifiers, request bodies, credentials, certificates, token contents, or exception messages. Request-derived correlation values are bounded and character-filtered. Error bodies are generic.

## Secret handling and operations

- Never commit database passwords, API credentials, RPC authentication details, private keys, production certificates, keystore passwords, truststore passwords, or bearer tokens.
- Supply MongoDB connection details through `MONGODB_URI`, `MONGODB_DATABASE`, `MONGODB_USERNAME`, `MONGODB_PASSWORD`, and `MONGODB_SSL_CERTIFICATE` where needed. Production MongoDB TLS is mandatory and hostname verification cannot be disabled.
- Supply `CHAIN_RPC_URL` and `SIGNER_KEY_REF` externally. `SIGNER_KEY_REF` is a reference only; this foundation does not sign chain transactions or implement a Vault/KMS/HSM client.
- Compose MongoDB and Anvil are local fixtures bound to loopback. Never use their deterministic accounts or unauthenticated local services with real assets.
- Do not log configuration values, request bodies, authorization headers, raw RPC URLs, or exception details that may include connection strings.

### Certificate rotation and revocation

1. Issue the replacement server certificate and authorized client trust anchors through the organization's CA and secret-management process. Keep private keys and passwords out of the repository and CI artifacts.
2. For a trust rotation, create a truststore containing the old and new approved client trust anchors, atomically update the mounted secret, then roll/restart every application instance so the new trust material is loaded.
3. Verify health probes without a certificate, then run the protected-request check with a newly issued client certificate and JWT. Monitor the sanitized security decision logs.
4. After all clients have rotated, remove the old trust anchor from the truststore, update the mounted secret, and roll/restart every instance again.
5. For a compromised client, remove its dedicated client issuing CA/trust anchor and roll/restart all instances immediately. If clients share a CA trust anchor, rotate that CA and reissue affected clients; this configuration does not provide per-leaf CRL/OCSP revocation. Confirm the compromised certificate is rejected before restoring traffic.
6. For suspected server-key compromise, replace the server key/certificate and revoke the old certificate with the issuing CA, then roll/restart instances and verify clients reject the old server identity.

The deployment operator owns truststore generation, secret mounting, rollout coordination, certificate inventory, issuer compromise handling, and incident response. The application does not hot-reload changed trust files.

## Verification and CI gates

From the repository root, run:

```powershell
.\gradlew.bat :modules:contracts:test --no-daemon
.\gradlew.bat :modules:contracts:apiTest :modules:contracts:integrationTest --no-daemon
.\gradlew.bat :modules:contracts:test :modules:contracts:apiTest :modules:contracts:integrationTest --no-daemon
npm run contracts:compile
npm run contracts:test
npm run contracts:lint
bash tests/e2e-smoke.sh
```

The Gradle security tests cover filter order, route inventory, authorization, safe failures, exact CSP and other security headers, production-profile requirements, JWT claims and token types, and asset/tenant decisions. The API/integration tasks cover controller and messaging behavior. Contract commands require Node.js 22/npm. The Docker smoke test requires Docker Compose, Node.js 22/npm, Bash, and curl; it verifies the exact CSP, public health route, and fail-closed protected API access. For live protected TLS checks, supply the complete `APP_SECURITY_E2E_*` test issuer, trusted/untrusted client certificate, and CA set described in [deployment security operations](../deployment/SECURITY.md). These protected external fixtures are opt-in and were not exercised unless specifically provisioned.

CI retains its existing failure thresholds: npm audit fails at high severity, Trivy filesystem and image scans fail on high/critical unfixed findings, and Gitleaks fails on detected secrets. Do not weaken those thresholds to make a change pass. These scans and tests do not replace an identity-provider review, production truststore validation, contract audit, threat model, or incident exercise.
