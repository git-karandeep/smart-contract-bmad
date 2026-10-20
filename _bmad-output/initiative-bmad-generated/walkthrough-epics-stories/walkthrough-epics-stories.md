# Epic and Story Documentation Walkthrough

**Review:** `epics-stories`  
**Review status:** Sharded walkthrough for complete epic/story coverage; no blocks accepted.  
**Current block:** Intent.  
**Narrative changes:** Changed to shard all work; every epic and individual story document is covered.

## 1. Intent

- [ ] **Not started — current block**

The documented goal is a regulated digital-asset platform that supports authorized token creation, investor verification, compliant transfers, blockchain settlement and off-chain reconciliation. Its transfer requirements include asynchronous REST intake, confirmation-aware finality, Kafka events, and status/audit queries; the non-functional requirements call for availability, idempotency, encryption, scalability, auditability, and explicit reconciliation. The foundation and security epics describe the engineering and access-control groundwork for those goals; this walkthrough follows the written scope without adding goals.

**Source:** [Product requirements](../../../docs/prd/requirements.md), [Epic 0 goal](../../../docs/epics/epic-00-setup.md), [Epic 1 goal](../../../docs/epics/epic-01-security.md), and [Epic 2 goal](../../../docs/epics/epic-02-wallet.md).

## 2. Broad strokes

- [ ] **Not started**

These entry points show the product outcomes, three populated epic paths, and the story organization:

1. [Product requirements](../../../docs/prd/requirements.md) — functional outcomes and platform-wide quality requirements.
2. [Epic 0 — Project Foundation & Engineering Setup](../../../docs/epics/epic-00-setup.md) — repository, application, infrastructure, and delivery foundations.
3. [Epic 1 — Enterprise API Security and Access Control](../../../docs/epics/epic-01-security.md) — the request path from trusted transport through identity, authorization, and operational controls.
4. [Epic 2 — Wallet-Based Token Movement](../../../docs/epics/epic-02-wallet.md) — the transfer lifecycle from instruction through compliance, blockchain finality, and reconciliation.
5. [Story catalog](../../../docs/stories/README.md) — the catalog of story documents organized under their epics.

## 3. Epic 0 — Repository and application baseline

- [ ] **Not started**

The foundation begins with a consistent repository and traceable project structure, then adds the Java build and a runnable Spring Boot service. These stories establish the workspace and application entry point on which later infrastructure and product work depends.

- [Epic 0 definition and completion goals](../../../docs/epics/epic-00-setup.md)
- [Story 0.1 — Initialize Git Repository](../../../docs/stories/epic-00-stories/Story-0.1.md), [Story 0.2 — Establish BMAD Project Structure](../../../docs/stories/epic-00-stories/Story-0.2.md), [Story 0.3 — Create Gradle Java Project](../../../docs/stories/epic-00-stories/Story-0.3.md), and [Story 0.4 — Bootstrap Spring Boot Application](../../../docs/stories/epic-00-stories/Story-0.4.md).

## 4. Epic 0 — Runtime configuration and local infrastructure

- [ ] **Not started**

Environment profiles separate configuration, while persistence, messaging, and a local EVM provide the services the application needs to run. The sequence moves from configurable runtime settings to the database, event broker, and blockchain network.

- [Story 0.5 — Configure Application Profiles](../../../docs/stories/epic-00-stories/Story-0.5.md), [Story 0.6 — Establish Database Infrastructure](../../../docs/stories/epic-00-stories/Story-0.6.md), [Story 0.7 — Establish Kafka Infrastructure](../../../docs/stories/epic-00-stories/Story-0.7.md), and [Story 0.8 — Establish Local Blockchain Network](../../../docs/stories/epic-00-stories/Story-0.8.md).

## 5. Epic 0 — Contract connectivity and operational visibility

- [ ] **Not started**

The smart-contract project is built independently, then connected to Java through Web3j and the configured RPC network. Local orchestration and baseline observability make this development stack reachable and diagnosable.

- [Story 0.9 — Create Solidity Project](../../../docs/stories/epic-00-stories/Story-0.9.md), [Story 0.10 — Configure Web3j Integration](../../../docs/stories/epic-00-stories/Story-0.10.md), [Story 0.11 — Configure Docker Compose](../../../docs/stories/epic-00-stories/Story-0.11.md), and [Story 0.12 — Establish Observability](../../../docs/stories/epic-00-stories/Story-0.12.md).

## 6. Epic 0 — Security, verification, and delivery

- [ ] **Not started**

The remaining foundation stories put baseline controls and automated test layers in place, then carry those checks into CI and developer setup guidance. Together, they describe how subsequent changes are checked and how the environment is operated by contributors.

- [Story 0.13 — Establish Security Baseline](../../../docs/stories/epic-00-stories/Story-0.13.md), [Story 0.14 — Configure Automated Testing](../../../docs/stories/epic-00-stories/Story-0.14.md), [Story 0.15 — Establish CI Pipeline](../../../docs/stories/epic-00-stories/Story-0.15.md), and [Story 0.16 — Create Developer Documentation](../../../docs/stories/epic-00-stories/Story-0.16.md).

## 7. Epic 1 — Transport boundary and caller authentication

- [ ] **Not started**

The security request path starts at the trusted TLS/mTLS termination boundary, continues into a centralized deny-by-default filter chain, and establishes a principal only from approved credentials. The stories distinguish transport identity, route policy, and caller authentication as successive controls.

- [Epic 1 architecture and implementation sequence](../../../docs/epics/epic-01-security.md)
- [Story 1.1 — Establish the transport security boundary](../../../docs/stories/epic-01-stories/Story-1.1.md), [Story 1.2 — Centralize request security and deny by default](../../../docs/stories/epic-01-stories/Story-1.2.md), and [Story 1.3 — Authenticate callers and validate credentials](../../../docs/stories/epic-01-stories/Story-1.3.md).

## 8. Epic 1 — Permissions, browser policy, and security outcomes

- [ ] **Not started**

After identity is established, endpoint permissions and asset/tenant entitlements constrain what a caller may do. Browser response policy and consistent failure/audit handling then carry those decisions to clients and operations without exposing sensitive data.

- [Story 1.4 — Enforce role- and scope-based access](../../../docs/stories/epic-01-stories/Story-1.4.md), [Story 1.5 — Enforce asset and tenant entitlements](../../../docs/stories/epic-01-stories/Story-1.5.md), [Story 1.6 — Apply a restrictive Content Security Policy](../../../docs/stories/epic-01-stories/Story-1.6.md), and [Story 1.7 — Standardize security failures and audit events](../../../docs/stories/epic-01-stories/Story-1.7.md).

## 9. Epic 1 — Operations and end-to-end verification

- [ ] **Not started**

Operational procedures maintain trust and verify controls after release; a cleanup pass consolidates the preceding security work before the closing suite checks that the request path works together. The sequence ends with verification rather than adding another security policy layer.

- [Story 1.8 — Document security operations and verify controls](../../../docs/stories/epic-01-stories/Story-1.8.md), [Story 1.9 — Refactor sweep](../../../docs/stories/epic-01-stories/Story-1.9.md), and [Story 1.10 — Verify the complete security request path](../../../docs/stories/epic-01-stories/Story-1.10.md).

## 10. Epic 2 — Transfer intake, eligibility, and orchestration

- [ ] **Not started**

Each transfer starts as a durable instruction with an authenticated initiator, then passes wallet, network, token, and authorization checks before workflow progression. Camunda provides the durable lifecycle and explicit paths that connect the instruction to screening and settlement.

- [Epic 2 workflow, business rules, and acceptance criteria](../../../docs/epics/epic-02-wallet.md)
- [Story 2.1 — Create transfer instruction API and persistence model](../../../docs/stories/epic-02-stories/Story-2.1.md), [Story 2.2 — Validate wallet addresses and transfer eligibility](../../../docs/stories/epic-02-stories/Story-2.2.md), and [Story 2.3 — Orchestrate the token movement lifecycle in Camunda](../../../docs/stories/epic-02-stories/Story-2.3.md).

## 11. Epic 2 — Screening requests and correlated results

- [ ] **Not started**

The workflow requests KYC, AML, and sanctions decisions using provider-specific context and stable instruction/request identifiers. Authenticated result ingestion validates and correlates asynchronous responses so that a response cannot satisfy another transfer's screening wait.

- [Story 2.4 — Request KYC screening for transfer parties](../../../docs/stories/epic-02-stories/Story-2.4.md), [Story 2.5 — Request AML screening for transfers](../../../docs/stories/epic-02-stories/Story-2.5.md), [Story 2.6 — Request sanctions screening for transfer parties](../../../docs/stories/epic-02-stories/Story-2.6.md), and [Story 2.7 — Ingest authenticated screening results](../../../docs/stories/epic-02-stories/Story-2.7.md).

## 12. Epic 2 — Compliance gate and execution boundary

- [ ] **Not started**

Compliance aggregation waits for all mandatory decisions and keeps rejection, review, error, and timeout outcomes non-executable. Immediately before submission, the workflow rechecks current authorization and transfer conditions; only a current approved instruction reaches the Web3j adapter.

- [Story 2.8 — Aggregate compliance decisions and enforce timeouts](../../../docs/stories/epic-02-stories/Story-2.8.md), [Story 2.9 — Revalidate transfer immediately before execution](../../../docs/stories/epic-02-stories/Story-2.9.md), and [Story 2.10 — Submit compliant transfers through a Web3j adapter](../../../docs/stories/epic-02-stories/Story-2.10.md).

## 13. Epic 2 — Contract restrictions, finality, and reconciliation

- [ ] **Not started**

Contract restrictions complement the off-chain compliance gate, while transaction monitoring distinguishes submission from execution and configured finality. Reconciliation compares authoritative chain data with the original instruction before reporting completion.

- [Story 2.11 — Enforce token transfer restrictions in Solidity](../../../docs/stories/epic-02-stories/Story-2.11.md), [Story 2.12 — Track blockchain transaction status and finality](../../../docs/stories/epic-02-stories/Story-2.12.md), and [Story 2.13 — Reconcile confirmed transactions and finalize transfer status](../../../docs/stories/epic-02-stories/Story-2.13.md).

## 14. Epic 2 — Recovery, audit, and transfer verification

- [ ] **Not started**

Idempotency and recovery preserve one logical transfer across retries, duplicate messages, restarts, and uncertain submissions. Audit/monitoring and the closing end-to-end suite trace those lifecycle decisions and exercise both safe completion and failure paths.

- [Story 2.14 — Make transfer processing idempotent and recoverable](../../../docs/stories/epic-02-stories/Story-2.14.md), [Story 2.15 — Audit transfer decisions and monitor workflow health](../../../docs/stories/epic-02-stories/Story-2.15.md), and [Story 2.16 — Verify end-to-end transfer security and failure handling](../../../docs/stories/epic-02-stories/Story-2.16.md).

## 15. Periphery

- [ ] **Not started**

- [Epic 3 tokenization document](../../../docs/epics/epic-03-wallet-standardization.md) — included in the epic set; the source document is empty.
- [Epic index](../../../docs/epics/README.md) — lists the current epic entry point.
- [Story catalog](../../../docs/stories/README.md) — indexes story documents.
- [Requirement-to-code traceability](../../../docs/traceability.md) — describes the path from requirements and stories to design, source, and tests.
- [Product requirements](../../../docs/prd/requirements.md) — defines the functional and non-functional requirements behind the walkthrough.
