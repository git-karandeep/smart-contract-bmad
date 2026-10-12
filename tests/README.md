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
| Contract compile and tests | `npm ci && npm run contracts:compile && npm run contracts:test` | Node.js 22; internet access on first compiler download |
| Solidity static checks | `npm run contracts:lint` | Node.js dependencies from `npm ci` |
| End-to-end stack smoke | `bash tests/e2e-smoke.sh` | Docker Compose, Node.js 22/npm, Bash, and curl; builds the sample contract and starts MongoDB, single-node KRaft Kafka (no ZooKeeper), Anvil, and the app. Optional `APP_SECURITY_E2E_*` settings add a protected HTTPS/JWT/client-certificate check against an external test deployment; see [security operations](../deployment/SECURITY.md). |

`.\gradlew.bat check` / `./gradlew check` runs the default Gradle verification tasks. The end-to-end script uses its own Compose project and removes its containers and volumes on exit. The normal local Compose stack keeps its named volumes; use `docker compose -f deployment/docker-compose.yml down -v` only when intentionally deleting volumes used by that stack. ZooKeeper-era volumes are unreferenced and are not migrated or automatically removed.

Workflow execution, full business-flow API tests, reorg/chaos tests, and contract fuzz/invariant tests have not been implemented by this foundation and are not implied by these commands.
