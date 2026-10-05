# Functional and Non-functional Requirements

## Functional requirements

### FR-001 Token creation
The system shall allow an authorized issuer to create a digital asset token.

### FR-002 Investor verification
The system shall verify investor eligibility before allowing token transfer.

### FR-003 Token transfer
The system shall validate compliance rules before executing a transfer.

### FR-004 Settlement
The system shall coordinate blockchain settlement and off-chain reconciliation.

### FR-005 Transfer API
The system shall accept a transfer request via REST and return a business id immediately for async processing.

### FR-006 Confirmation handling
The system shall wait for the configured confirmation depth before marking a settlement as final.

### FR-007 Event publication
The system shall publish domain events to Kafka on each state transition.

### FR-008 Status and audit queries
The system shall allow the caller to query transfer status and the related audit trail.

## Non-functional requirements

- 99.9% availability for processing-critical steps.
- Idempotent transaction processing across retries.
- End-to-end auditability and correlation.
- Encryption for sensitive data and key material.
- Horizontal scalability for workers and consumers.
- Eventual consistency handling with explicit reconciliation.
