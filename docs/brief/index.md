# Project Brief

This brief captures the business and technical intent for the chain settlement platform. It is intentionally concise and designed to be extended by the PRD and architecture shards.

## 1. Problem

Parties need a shared, tamper-evident record of ownership and settlement, but the operational steps around approval, validation, retries, exception handling, and human intervention cannot live entirely inside a smart contract.

## 2. Solution

The platform separates concerns:

- Smart contracts enforce ownership and settlement finality.
- Camunda 8 orchestrates the long-running business process.
- Kafka propagates asynchronous events.
- Web3j provides blockchain connectivity.
- MongoDB stores domain projections, outbox records, and audit data.
- Spring Boot hosts the API and integration glue.

## 3. Business goals

- Eliminate duplicate or double-spend settlement attempts.
- Provide traceability from business event to blockchain transaction to Kafka event.
- Enable reconciliation when chain or worker state diverges.
- Preserve a recoverable workflow even during external outages or reorgs.

## 4. Assumptions

- EVM-compatible chain and solidity contracts are the source of truth.
- Camunda 8 handles orchestration and timers.
- MongoDB is used as an off-chain projection and workflow store.
- Java 21 and Spring Boot 3.x are the runtime baseline.

## 5. Success criteria

- A settlement is never executed twice.
- Every process can be explained from business id to transaction hash to event.
- Projections can be rebuilt from chain events.
- Transient failures do not leave the platform in an unrecoverable state.

## 6. Related docs

- [PRD](../prd/index.md)
- [Architecture](../architecture/index.md)
- [API spec](../api-spec.md)
- [Security](../security.md)
