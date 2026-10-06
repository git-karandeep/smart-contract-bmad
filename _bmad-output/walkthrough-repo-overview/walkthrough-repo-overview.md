# Review narrative: smart-contracts-bmad

Current block: [ ] Intent

- [ ] Intent

  This repository is a draft smart-contract settlement platform: a Java/Spring multi-module system that uses Solidity for settlement invariants, Camunda for long-running workflow orchestration, Kafka for event distribution, and MongoDB for projections and workflow data. The intent is documented in [README](../../README.md), and the module map is visible under [modules](../../modules).

  The repo's design goal is to keep on-chain settlement authoritative while making the surrounding process observable, recoverable, and auditable. That intent is visible in the business flow and requirements captured in [README](../../README.md) and the architecture artifacts under [docs](../../docs).

- [ ] Broad strokes

  The fastest way to understand this project is to read the entry points in order:

  1. [README](../../README.md) — project brief, requirements summary, architecture, and operational goals.
  2. [settings.gradle](../../settings.gradle) — top-level module graph showing the service boundaries.
  3. [build.gradle](../../build.gradle) — Java 21 / Spring Boot / Camunda / Mongo / Kafka setup.
  4. [modules](../../modules) — runtime modules for API, domain, workers, chain integration, and contracts.

  Taken together, the code is organized around one main mechanism: inbound API requests become workflow state, workers call chain adapters, every state transition emits events, and MongoDB stores the auditable projection.

- [ ] Slices: workflow and integration boundaries

  Mechanism: the critical review question is where responsibility moves between the application, workflow engine, and blockchain adapter. The code is structured to keep settlement logic on-chain and orchestration logic in Java/Camunda, while Kafka and MongoDB provide the integration and audit trail.

  Specific places to inspect next:

  - [modules/api-gateway](../../modules/api-gateway) — API surface and request acceptance.
  - [modules/process-definitions](../../modules/process-definitions) — BPMN/DMN definitions driving approvals, retries, and timeouts.
  - [modules/process-workers](../../modules/process-workers) — job workers that execute validation and settlement tasks.
  - [modules/chain-adapter](../../modules/chain-adapter) — blockchain client abstraction for signing and transaction submission.
  - [modules/contracts](../../modules/contracts) — Solidity contracts and contract-facing application layer.
  - [modules/persistence-mongo](../../modules/persistence-mongo) — Mongo-backed projections and outbox-style storage.

- [ ] Periphery

  Supporting material is intentionally separate from runtime code and acts as enabling documentation rather than execution logic:

  - [docs/brief/index.md](../../docs/brief/index.md) — product brief and strategic framing.
  - [docs/architecture/index.md](../../docs/architecture/index.md) — architecture entry points.
  - [docs/api-spec.md](../../docs/api-spec.md) — API contract documentation.
  - [docs/security.md](../../docs/security.md) — security considerations and operational expectations.
