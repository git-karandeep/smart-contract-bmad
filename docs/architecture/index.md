# Architecture Index

This directory contains the sharded architecture documentation for the platform. The companion source documents remain in the repository root, but the detailed architecture is organized by concern for easier maintenance and review.

## Principal shards

- [Technology stack](tech-stack.md)
- [Repository and Gradle structure](source-tree.md)
- [Coding standards](coding-standards.md)

## Architecture summary

The system follows a hexagonal design with a distinct responsibility split:

- Camunda handles orchestration, timers, and human approval workflows.
- MongoDB stores domain projections, outbox data, and audit records.
- Smart contracts enforce ownership and settlement invariants on-chain.
- Web3j and a chain adapter manage blockchain interaction and transaction recovery.
- Kafka and an outbox pattern support asynchronous propagation and replay-safe integration.

## Related documentation

- [Project brief](../brief/index.md)
- [PRD](../prd/index.md)
- [Architecture overview](../architecture.md)
