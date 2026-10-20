# Stories

## Epic 0: Project Foundation & Engineering Setup

- [Story 0.1: Initialize Git Repository](epic-00-stories/Story-0.1.md)
- [Story 0.2: Establish BMAD Project Structure](epic-00-stories/Story-0.2.md)
- [Story 0.3: Create Gradle Java Project](epic-00-stories/Story-0.3.md)

## Epic 1: Enterprise API Security and Access Control

- [Story 1.1: Establish the transport security boundary](epic-01-stories/Story-1.1.md)
- [Story 1.2: Centralize request security and deny by default](epic-01-stories/Story-1.2.md)
- [Story 1.3: Authenticate callers and validate credentials](epic-01-stories/Story-1.3.md)
- [Story 1.4: Enforce role- and scope-based access](epic-01-stories/Story-1.4.md)
- [Story 1.5: Enforce asset and tenant entitlements](epic-01-stories/Story-1.5.md)
- [Story 1.6: Apply a restrictive Content Security Policy](epic-01-stories/Story-1.6.md)
- [Story 1.7: Standardize security failures and audit events](epic-01-stories/Story-1.7.md)
- [Story 1.8: Document security operations and verify controls](epic-01-stories/Story-1.8.md)
- [Story 1.9: Refactor sweep](epic-01-stories/Story-1.9.md)
- [Story 1.10: Verify the complete security request path](epic-01-stories/Story-1.10.md)

## Epic 2: Wallet-Based Token Movement

- [Story 2.1: Create transfer instruction API and database model](epic-02-stories/Story-2.1.md) — 5 points
- [Story 2.2: Validate wallet addresses and transfer eligibility](epic-02-stories/Story-2.2.md) — 3 points
- [Story 2.3: Create Camunda token movement BPMN workflow](epic-02-stories/Story-2.3.md) — 5 points
- [Story 2.4: Initiate KYC screening and correlate results](epic-02-stories/Story-2.4.md) — 3 points
- [Story 2.5: Initiate AML screening and correlate results](epic-02-stories/Story-2.5.md) — 3 points
- [Story 2.6: Initiate sanctions screening and correlate results](epic-02-stories/Story-2.6.md) — 3 points
- [Story 2.7: Implement authenticated screening-message ingestion](epic-02-stories/Story-2.7.md) — 5 points
- [Story 2.8: Aggregate compliance decisions and enforce timeouts](epic-02-stories/Story-2.8.md) — 5 points
- [Story 2.9: Implement pre-execution compliance and transfer validation](epic-02-stories/Story-2.9.md) — 3 points
- [Story 2.10: Implement Web3j blockchain adapter](epic-02-stories/Story-2.10.md) — 5 points
- [Story 2.11: Implement and test Solidity transfer restrictions](epic-02-stories/Story-2.11.md) — 3 points
- [Story 2.12: Track blockchain transaction status and finality](epic-02-stories/Story-2.12.md) — 5 points
- [Story 2.13: Implement reconciliation and final transfer status](epic-02-stories/Story-2.13.md) — 5 points
- [Story 2.14: Implement idempotency, retries, and recovery](epic-02-stories/Story-2.14.md) — 5 points
- [Story 2.15: Implement audit trail, monitoring, and alerts](epic-02-stories/Story-2.15.md) — 5 points
- [Story 2.16: Complete end-to-end transfer security testing](epic-02-stories/Story-2.16.md) — 5 points

## Epic 3: Standardized BPMN Design and Reusable Workflow Generator

- [Story 3.1: Build the BPMN standardization generator](epic-03-stories/Story-3.1.md)
- [Story 3.2: Generate reusable screening processes](epic-03-stories/Story-3.2.md)
- [Story 3.3: Invoke screening through Call Activities](epic-03-stories/Story-3.3.md)
- [Story 3.4: Standardize screening retries and failures](epic-03-stories/Story-3.4.md)
- [Story 3.5: Prevent unsafe payment resubmission](epic-03-stories/Story-3.5.md)
- [Story 3.6: Produce a machine-readable generation report](epic-03-stories/Story-3.6.md)
- [Story 3.7: Refactor sweep](epic-03-stories/Story-3.7.md)
- [Story 3.8: Verify generated workflows in Camunda](epic-03-stories/Story-3.8.md)

See the [epic index](../epics/README.md) and the
[requirement-to-code traceability guide](../traceability.md) for the path from
requirements and acceptance criteria to design, source, and tests.
