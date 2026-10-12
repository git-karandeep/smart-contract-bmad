# Test organization

Tests are maintained with their owning Gradle module under
`modules/<module>/src/test/`; do not duplicate module tests in this directory.
The Solidity suite is under `modules/solidity-contract/src/test/`.

## Layers and commands

Run from the repository root:

| Layer | Command | Requirements |
|---|---|---|
| Java unit and application configuration | `.\gradlew.bat test` (Windows) or `./gradlew test` | JDK 21; no external services |
| Adapter and messaging integration | `.\gradlew.bat integrationTest` or `./gradlew integrationTest` | JDK 21; in-process JSON-RPC endpoint and embedded Kafka |
| REST API validation | `.\gradlew.bat apiTest` or `./gradlew apiTest` | JDK 21; no external services |
| Contract compile, tests, and static checks | `npm ci && npm run contracts:compile && npm run contracts:test && npm run contracts:lint` | Node.js 22; internet access on first compiler download |
| End-to-end stack smoke | `bash tests/e2e-smoke.sh` | Docker Compose, Node.js 22/npm, Bash, and curl; builds the sample contract and starts MongoDB, single-node KRaft Kafka (no ZooKeeper), Anvil, and the app. Optional `APP_SECURITY_E2E_*` settings add a protected HTTPS/JWT/client-certificate check against an external test deployment; see [security operations](../deployment/SECURITY.md). |

`.\gradlew.bat check` / `./gradlew check` runs the default Gradle verification tasks. The end-to-end script uses its own Compose project and removes its containers and volumes on exit. The normal local Compose stack keeps its named volumes; use `docker compose -f deployment/docker-compose.yml down -v` only when intentionally deleting volumes used by that stack. ZooKeeper-era volumes are unreferenced and are not migrated or automatically removed.

For the Epic 01 security boundary, run `.\gradlew.bat :modules:contracts:test :modules:contracts:apiTest :modules:contracts:integrationTest --no-daemon` (or the equivalent `./gradlew` command), all three contract commands above, and `bash tests/e2e-smoke.sh`. The smoke test checks the complete CSP header and local fail-closed behavior. The optional protected TLS/JWT checks require every `APP_SECURITY_E2E_*` fixture described in [security operations](../deployment/SECURITY.md); without those external fixtures, do not report the protected production-like transport path as exercised.

Workflow execution, full business-flow API tests, reorg/chaos tests, and contract fuzz/invariant tests have not been implemented by this foundation and are not implied by these commands.
