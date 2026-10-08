Story 0.6 — Establish Database Infrastructure

As a developer,
I want mongoDB configured,
so that transactional and blockchain state can be persisted.

Acceptance Criteria
mongoDB runs locally
Application connects successfully
Database migration framework is configured
Migration executes automatically
Connection failure is handled gracefully
Initial tables
transaction
blockchain_transaction
wallet
asset
token
audit_event

Don't implement the complete domain model yet; this story establishes the infrastructure.