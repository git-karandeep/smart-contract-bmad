## 10. Testing Strategy (BMAD `coding-standards.md` shard)

| Level | Scope | Tools |
|---|---|---|
| Unit | Domain logic, mappers | JUnit 5, AssertJ |
| Contract | Solidity behavior | Foundry/Hardhat tests, fuzz/invariants |
| Component | Mongo repositories, outbox, idempotency | Testcontainers (MongoDB replica set) |
| Process | BPMN paths, DMN, timers, error flows | Camunda Process Test (embedded/Testcontainers) |
| Integration | Worker ↔ chain-adapter ↔ local chain | Testcontainers + Anvil/Besu |
| E2E | API → process → chain → projection | docker-compose env, Karate/RestAssured |
| Non-functional | Throughput, confirmation latency, failure injection | Gatling/k6, Toxiproxy |

Coding standards: constructor injection only, no business logic in controllers/workers, records for immutable DTOs, ArchUnit tests enforcing module rules, Spotless + Checkstyle in `check`, 80% line coverage on `common-domain` and `domain-service`.

---