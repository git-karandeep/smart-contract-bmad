
## 4. Repository and Gradle Structure (BMAD `source-tree.md` shard)

```
smart-contracts-bmad/
├── build-logic/                       # convention plugins
│   └── src/main/kotlin/
│       ├── java-conventions.gradle.kts
│       ├── spring-service-conventions.gradle.kts
│       └── quality-conventions.gradle.kts   # spotless, checkstyle, jacoco
├── gradle/
│   └── libs.versions.toml             # single source of dependency versions
├── settings.gradle.kts
├── build.gradle.kts
├── modules/
│   ├── common-domain/                 # pure Java: value objects, domain events, ports
│   ├── common-observability/          # correlation id, logging, tracing
│   ├── contracts/                     # Solidity sources + generated Web3j wrappers
│   │   ├── src/main/solidity/
│   │   └── build.gradle.kts           # web3j plugin
│   ├── chain-adapter/                 # implements ChainPort via Web3j, tx manager, retry
│   ├── chain-listener/                # event subscription, confirmations, reorg handling
│   ├── persistence-mongo/             # documents, repositories, outbox, idempotency store
│   ├── process-definitions/           # BPMN/DMN/forms + deploy task (no Java logic)
│   │   └── src/main/resources/bpmn/
│   ├── process-workers/               # Zeebe job workers (thin; delegate to domain)
│   ├── domain-service/                # use cases / application layer
│   ├── api-gateway/                   # REST controllers, authN/Z, OpenAPI
│   └── app-bootstrap/                 # Spring Boot main(s), wiring, profiles
├── deployment/
│   ├── docker-compose.yml             # local: zeebe, elastic, mongo(rs), anvil/besu
│   └── helm/
├── docs/
│   ├── prd.md
│   ├── api-spec.md
│   ├── architecture.md
│   ├── architecture-flow.md
│   ├── data-model.md
│   ├── security.md
│   ├── traceability.md
│   ├── architecture/                  # includes index, tech-stack, source-tree, standards, security pointer
│   ├── prd/                           # sharded: requirements.md, actors-and-success.md, etc
│   ├── brief/
│   ├── epics/
│   ├── stories/
│   └── decisions/                     # architecture decision records
├── tests/                             # test organization index; tests live with modules
├── .bmad/                             # compatibility pointer to active BMAD configuration
├── _bmad/                             # active BMAD configuration and scripts
├── _bmad-output/                      # generated BMAD artifacts
└── .bmad-core/                        # BMAD agents/tasks/templates
```
