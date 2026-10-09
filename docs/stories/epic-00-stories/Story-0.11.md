Story 0.11 — Configure Docker Compose

As a developer,
I want the complete development infrastructure available through Docker Compose,
so that a new developer can start the platform consistently.

Infrastructure
Docker Compose
│
├── Spring Boot
├── mongoDB
├── Kafka
├── Kafka UI
└── Local Blockchain
Command
docker compose up
Acceptance Criteria

- A clean machine can start the documented development environment with a healthy single-node KRaft broker and no ZooKeeper service.
- The application and Kafka UI remain reachable at their documented endpoints; application bootstrap configuration and Kafka messaging behavior are unchanged.
- Compose uses a new KRaft data volume and does not migrate, reuse, or automatically delete existing Kafka/ZooKeeper-era volumes.
- End-to-end smoke verification confirms KRaft quorum readiness, application connectivity, expected topics, and event consumption using an isolated Compose project.