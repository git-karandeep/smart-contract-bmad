## 3. Technology Stack (BMAD `tech-stack.md` shard)

| Layer | Choice | Notes |
|---|---|---|
| Language/Runtime | Java 21 | Virtual threads optional for IO-bound workers |
| Framework | spring boot 4.x | |
| Build | Gradle 8.x, Kotlin DSL, version catalog, convention plugins | |
| Orchestration | Camunda 8 (Zeebe) + Spring Zeebe SDK | Verify current client artifact for your Camunda version |
| Persistence | MongoDB 7.x (replica set required for transactions) | Spring Data MongoDB |
| Blockchain client | Web3j (+ Gradle web3j plugin for contract wrappers) | |
| Contracts | Solidity + Foundry or Hardhat | Compiled ABI/bytecode consumed by Gradle |
| Messaging (optional) | Kafka | Only if fan-out beyond Zeebe is needed |
| Observability | OpenTelemetry, Micrometer, Prometheus, Grafana, Loki | |
| Testing | JUnit 5, Testcontainers (Mongo, Zeebe, Anvil/Besu), Camunda Process Test | |
| Packaging/Deploy | Docker, Helm, Kubernetes | |
| Secrets/Keys | Vault / cloud KMS / HSM | |

---