Story 0.7 — Establish Kafka Infrastructure

As a developer,
I want Kafka available locally,
so that the platform can use event-driven communication.

Initial topics
tokenization.events
transfer.events
compliance.events
settlement.events
blockchain.events
Acceptance Criteria
- Kafka starts locally in single-node KRaft mode with no ZooKeeper service.
- Application connects using the existing bootstrap-server contract.
- Producer and consumer behavior, serialization, topics, and failed-message logging/retry remain unchanged.
- KRaft uses a new persistent volume; existing Kafka and ZooKeeper-era volumes are left untouched and are not migrated or automatically deleted.