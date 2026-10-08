# Security baseline

This repository provides a local-development foundation, not a production-ready identity, key-custody, or deployment platform.

## Credentials and signing keys

- Never commit database passwords, API credentials, RPC authentication details, private keys, or production certificates.
- Supply production values through the deployment secret manager. Required application settings are `MONGODB_URI`, `CHAIN_RPC_URL`, `SIGNER_KEY_REF`, `APP_SECURITY_USERNAME`, and `APP_SECURITY_PASSWORD`.
- `SIGNER_KEY_REF` is a reference only. This foundation does not sign chain transactions or implement a Vault/KMS/HSM client.
- Compose runs MongoDB without authentication, bound to loopback only. Its Anvil accounts and the default local HTTP Basic credentials are public development fixtures. Never reuse them outside local development.
- Native local runs bind the application to loopback. The Compose container listens on its bridge interface, but the published host port remains loopback-only.
- Avoid logging configuration values, request bodies, authorization headers, raw RPC URLs, or exception details that may include connection strings. Database and chain connection failures are surfaced with sanitized top-level messages.

## Transport and access

- Production MongoDB TLS is enabled by default and startup rejects disabled TLS or disabled hostname verification. Provide a trusted CA certificate when required by the MongoDB deployment.
- The application currently uses HTTP Basic and cookie-backed CSRF protection as a minimal authentication boundary. It does not provide OAuth2/OIDC, fine-grained authorization, or user provisioning. Put it behind an authenticated gateway and TLS-terminating ingress before any shared or public deployment; do not expose the application directly over plaintext HTTP.
- Authenticated clients can fetch a CSRF token from `GET /api/security/csrf`. Send the returned token in the `X-XSRF-TOKEN` header and include the `XSRF-TOKEN` cookie on state-changing requests.
- Production `APP_SECURITY_USERNAME` and `APP_SECURITY_PASSWORD` must be supplied outside source control. Use strong, rotated credentials and TLS for every hop.
- Request, correlation, and transaction headers are restricted to a bounded safe character set before being copied to response headers, Kafka headers, or logging MDC.
- Order request fields have bean validation. Domain authorization, rate limiting, comprehensive request limits, and production API policy are not implemented.

## Data and chain boundaries

- Keep personal and confidential data off-chain. Put only required identifiers or hashes on-chain.
- MongoDB is the off-chain store; the chain adapter checks the configured chain ID before returning chain status. Do not use the deterministic Anvil development keys with real assets.
- The Solidity and chain setup is for local testing only. Contract audits, key custody, access-role operations, reorg recovery, and real-network deployment procedures remain required before production.

## Automated checks

CI runs npm dependency auditing, Trivy filesystem and container scans, Gitleaks secret detection, Solidity static checks, Gradle tests, Solidity tests, and the Docker Compose smoke test. CI fails when findings meet the configured severity thresholds. These tools do not replace manual threat modeling, contract audit, dependency review, or secret-manager controls.