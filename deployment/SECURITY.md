# Production TLS and security operations

## Deployment contract

The production application terminates TLS itself on port `8443`; no ingress, TLS proxy, or forwarded-certificate trust is configured in this repository. Clients validate the application server certificate. Configure a secret-mounted PKCS#12 server keystore and client truststore at `/run/secrets/server-keystore.p12` and `/run/secrets/client-truststore.p12`, or override the paths. Never bake either store or its password into the image.

Provide these runtime settings from the deployment secret/configuration service:

| Setting | Requirement |
| --- | --- |
| `SERVER_SSL_KEY_STORE_PATH` / `SERVER_SSL_KEY_STORE_PASSWORD` | Existing server PKCS#12 key store and password |
| `SERVER_SSL_TRUST_STORE_PATH` / `SERVER_SSL_TRUST_STORE_PASSWORD` | Existing PKCS#12 store containing only approved client trust anchors |
| `SERVER_SSL_ENABLED` | Must be `true` |
| `SERVER_SSL_CLIENT_AUTH` | Must be `want`: the connector requests but does not globally require a client certificate |
| `APP_SECURITY_JWT_ISSUER` / `APP_SECURITY_JWT_AUDIENCE` | Issuer and audience for the OAuth2 JWT resource server |

The `prod` profile fails startup when these properties are missing, HTTPS is disabled, client-auth is not `want`, or MongoDB TLS/hostname verification is weakened. JWT access tokens must have `typ` set to `at+jwt` or `JWT`. `/actuator/health` and its readiness/liveness descendants are the only unauthenticated routes and clients should omit certificates for them. Every protected route requires a valid JWT and a certificate trusted by the TLS connector. A missing certificate receives a generic `403` after authentication; a presented untrusted/invalid certificate fails the handshake. Other Actuator endpoints additionally require `OPS_READ`.

The `local` Compose deployment deliberately remains plain HTTP on a loopback-published port and has no identity provider or default password. It is a development stack, not a secure production model; its protected routes fail closed. Do not expose it to a shared/public network.

## Rotation and compromise response

The application reads keystore/truststore material at startup. Updating mounted files alone does not rotate a running instance.

1. Inventory server certificates, client trust anchors, owners, expiry dates, and dependent clients before rotation.
2. For routine rotation, distribute replacement server/client certificates through the organization's CA and secret manager. When changing a client trust anchor, temporarily include both old and new anchors in the mounted truststore, atomically update the secret, roll every instance, and verify the new certificates.
3. After client migration, remove the old anchor, update the secret, and roll every instance again. Verify a client signed by the retired anchor is rejected.
4. To revoke a compromised client, remove its dedicated issuing CA/trust anchor immediately, distribute the reduced truststore, and roll every instance. If client certificates share a trusted CA, rotate that CA and reissue all affected clients. There is no per-leaf CRL/OCSP integration in this application.
5. For a compromised server key, obtain and deploy a new server key/certificate, revoke the old certificate with its issuer, and roll every instance. Confirm clients reject the retired server identity.
6. Preserve sanitized application security logs and correlate incident timelines using `correlationId`; do not attach raw tokens, certificates, private keys, or unfiltered request data to incident records.

The deployment/identity operator owns trust-anchor generation, client identity lifecycle, secret distribution, rollout/restart, issuer compromise handling, and incident response. Recovery and rotation verification should be performed in a production-like environment before relying on these procedures.

## Protected TLS smoke test

The repository's Docker Compose smoke test always confirms public health behavior and fail-closed unauthenticated access in local mode. To add an external protected-route test against a production-like HTTPS deployment, provide all of the following when running `bash tests/e2e-smoke.sh`:

- `APP_SECURITY_E2E_URL`: HTTPS base URL for the application, such as `https://api.test.example:8443`
- `APP_SECURITY_E2E_BEARER_TOKEN`: short-lived test JWT whose issuer/audience are accepted and whose verified `scope` includes `CHAIN_STATUS_READ`
- `APP_SECURITY_E2E_CLIENT_CERT`, `APP_SECURITY_E2E_CLIENT_KEY`: authorized test client certificate and private key
- `APP_SECURITY_E2E_UNTRUSTED_CLIENT_CERT`, `APP_SECURITY_E2E_UNTRUSTED_CLIENT_KEY`: test client certificate and key signed by an untrusted CA
- `APP_SECURITY_E2E_CA`: CA bundle used by curl to validate the application server certificate

The script verifies the authorized request, requests with no certificate and an untrusted certificate, and a request with an invalid bearer token. It never prints the provided token or key. Do not use production credentials or certificates for this test; store ephemeral test values in the CI secret manager and ensure test JWTs and certificates have narrow scope and short lifetimes.
