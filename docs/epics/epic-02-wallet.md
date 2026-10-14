# EPIC TOK-03: Wallet-Based Token Movement and Compliance-Orchestrated Settlement

## 1. Epic Metadata

| Field                  | Description                                       |
| ---------------------- | ------------------------------------------------- |
| Epic ID                | TOK-03                                            |
| Epic Name              | Wallet-Based Token Movement                       |
| Priority               | Critical                                          |
| Domain                 | Blockchain / Digital Assets                       |
| Workflow Engine        | Camunda 8                                         |
| Backend                | Java / Spring Boot                                |
| Blockchain Integration | Web3j                                             |
| Smart Contracts        | Solidity                                          |
| Messaging              | Kafka                                             |
| Blockchain Network     | Hyperledger Besu / EVM-compatible                 |
| Persistence            | mongoDB                                        |
| Architecture           | Event-driven, asynchronous workflow orchestration |

## 2. Epic Goal

Enable an authorized wallet holder to initiate a token transfer from a source wallet address to a destination wallet address.

Camunda shall orchestrate the transfer lifecycle, including instruction creation, wallet and token validation, asynchronous KYC/AML/sanctions screening, compliance decision aggregation, Solidity smart-contract execution, blockchain confirmation, and final reconciliation.

The workflow shall execute the transfer only after all mandatory compliance checks have returned valid approval decisions.

The system shall support asynchronous responses from external compliance providers, durable workflow execution, idempotent processing, transaction tracking, and auditable outcomes.

## 3. Business Value

* Enable controlled token transfers between eligible wallet addresses.
* Automate compliance checks before blockchain execution.
* Coordinate asynchronous responses from multiple screening services.
* Prevent transfers that fail mandatory compliance requirements.
* Provide transaction-level traceability across the workflow and blockchain.
* Handle duplicate messages, temporary failures, and uncertain transaction outcomes safely.
* Support institutional digital asset and tokenization use cases.

## 4. Actors

1. Token Holder / Transfer Initiator
2. Source Wallet Owner
3. Destination Wallet Owner
4. Token Transfer API
5. Camunda Workflow Engine
6. KYC Service
7. AML Service
8. Sanctions Screening Service
9. Compliance Decision Service
10. Blockchain Adapter Service
11. Solidity Token Contract
12. Blockchain Event Listener
13. Reconciliation Service
14. Compliance Operations Team

## 5. Business Rules

### BR-001: Valid Transfer Instruction

A transfer instruction must contain:

* Unique instruction ID
* Source wallet address
* Destination wallet address
* Token contract address or token identifier
* Transfer amount
* Blockchain network identifier
* Initiating principal
* Idempotency key

### BR-002: Wallet Validation

Both wallet addresses must be valid for the selected network.

The system must verify the required ownership, control, authorization, and eligibility conditions before proceeding.

### BR-003: KYC Approval

The transfer must not proceed unless the applicable parties meet the required KYC eligibility rules.

### BR-004: AML Approval

The transfer must not proceed unless AML screening returns an acceptable decision under the configured policy.

### BR-005: Sanctions Approval

The transfer must not proceed unless sanctions screening returns an acceptable decision under the configured policy.

### BR-006: Compliance Decision Aggregation

The workflow must wait for all mandatory screening decisions or a defined timeout.

Any mandatory rejection or unresolved screening outcome must prevent blockchain execution.

A timeout must never be treated as an approval.

### BR-007: Blockchain Execution

The blockchain transaction may be submitted only after the compliance gate has approved the transfer and all other required validations have passed.

### BR-008: Transaction Finality

A submitted transaction must not be reported as successfully completed merely because an RPC endpoint accepted the submission.

The workflow must distinguish transaction submission, transaction inclusion, successful execution, and the network's configured confirmation or finality condition.

### BR-009: Idempotency

Duplicate instructions and duplicate external messages must not cause unintended duplicate transfers.

### BR-010: Auditability

All material state transitions and compliance decisions must be associated with the transfer instruction ID and recorded in an auditable manner.

## 6. High-Level Architecture

Transfer API
|
v
Spring Boot Token Movement Service
|
+---- mongoDB: Transfer Instruction
|
+---- Kafka: Transfer Events
|
v
Camunda 8 Workflow
|
+---- Validate Wallets and Token
|
+---- Request KYC Screening
|          |
|          v
|      KYC Service
|          |
|          v
|      KYC Result Message
|
+---- Request AML Screening
|          |
|          v
|      AML Service
|          |
|          v
|      AML Result Message
|
+---- Request Sanctions Screening
|          |
|          v
|      Sanctions Service
|          |
|          v
|      Sanctions Result Message
|
v
Compliance Decision Gate
|
+---- Rejected / Review / Timeout --> Hold or End
|
v
Blockchain Adapter
|
v
Web3j
|
v
Solidity Token Contract
|
v
Blockchain Transaction
|
v
Blockchain Event Listener
|
v
Confirmation and Reconciliation
|
v
Transfer Completed

## 7. Workflow Design

### Phase 1: Create Transfer Instruction

The client submits a transfer request.

The Token Movement API:

1. Authenticates the caller.
2. Authorizes the caller to initiate the requested transfer.
3. Validates the request schema.
4. Validates the network and wallet-address formats.
5. Verifies the caller's authority over the source wallet.
6. Validates the token identifier and amount.
7. Checks the idempotency key.
8. Creates a transfer instruction with status `RECEIVED`.
9. Starts the Camunda process using the unique instruction ID as the business key.

**Output:** `TransferInstructionCreated`

Example instruction:

{
"instructionId": "TRF-100001",
"sourceWallet": "0x1111111111111111111111111111111111111111",
"destinationWallet": "0x2222222222222222222222222222222222222222",
"tokenContract": "0x3333333333333333333333333333333333333333",
"amount": "100.00",
"networkId": 1337,
"status": "RECEIVED"
}

The addresses are illustrative only.

The amount must be represented internally using the token's configured decimal precision. Convert it to the token's integer base units before contract execution; do not use floating-point arithmetic.

### Phase 2: Validate Transfer Eligibility

Camunda invokes a Spring Boot service task to validate:

* Source and destination wallet formats.
* Token contract and network configuration.
* Token transfer restrictions.
* Source wallet authorization.
* Available balance, where applicable.
* Applicable transfer limits.
* Duplicate or conflicting instructions.

If validation fails, the workflow records the reason and terminates without invoking compliance screening or the smart contract.

**Output:** `TransferValidated` or `TransferValidationFailed`

### Phase 3: Request KYC, AML, and Sanctions Screening

Camunda creates the screening requests and correlates each response to the transfer instruction.

Each screening request includes:

* Instruction ID
* Screening request ID
* Source and destination wallet references
* Relevant verified identity or entity references
* Screening type
* Correlation ID
* Request timestamp
* Policy or screening version

The system must send only the information each screening provider is authorized to receive.

#### KYC Request

Purpose:
Verify the eligibility of the relevant parties under the applicable customer and jurisdictional requirements.

Expected results:

* `APPROVED`
* `REJECTED`
* `REVIEW_REQUIRED`
* `ERROR`

#### AML Request

Purpose:
Assess the transfer and relevant parties under the configured anti-money-laundering controls.

Expected results:

* `APPROVED`
* `REJECTED`
* `REVIEW_REQUIRED`
* `ERROR`

#### Sanctions Request

Purpose:
Determine whether the relevant parties or transaction require rejection, restriction, or additional review under applicable sanctions policies.

Expected results:

* `APPROVED`
* `REJECTED`
* `REVIEW_REQUIRED`
* `ERROR`

The workflow must apply the appropriate policy to each result. `REVIEW_REQUIRED` must not be interpreted as approval.

**Output:** `KycScreeningRequested`, `AmlScreeningRequested`, and `SanctionsScreeningRequested`

### Phase 4: Receive Asynchronous Screening Messages

The KYC, AML, and sanctions providers may respond at different times.

Each provider publishes a result message or calls an authenticated callback endpoint.

The message-ingestion service:

1. Authenticates the provider.
2. Validates the message schema and integrity.
3. Verifies the correlation and screening request IDs.
4. Confirms that the response belongs to the expected transfer and screening type.
5. Deduplicates the message.
6. Persists the result.
7. Correlates the result to the correct Camunda process instance.
8. Publishes an auditable screening-result event.

Example KYC result:

{
"instructionId": "TRF-100001",
"screeningRequestId": "KYC-200001",
"screeningType": "KYC",
"decision": "APPROVED",
"correlationId": "TRF-100001",
"screenedAt": "2026-10-11T18:30:00Z"
}

The timestamps and identifiers are illustrative.

#### Message Correlation

Use Camunda message correlation with a unique business correlation key and screening-specific identifiers.

A message name such as `KycScreeningResult` can identify the message type, while a correlation key identifies the relevant transfer or screening request.

Do not correlate messages using wallet address alone because a wallet may participate in many concurrent transfers.

For Camunda 8, model the wait states using message catch events or an equivalent supported message-correlation pattern. Configure message subscriptions and correlation keys consistently with the selected Camunda version.

### Phase 5: Aggregate Compliance Decisions

The workflow waits until:

* All mandatory screening decisions have arrived; or
* A configured timeout or operational exception occurs.

The compliance decision service evaluates the collected results.

| KYC             | AML      | Sanctions | Result                             |
| --------------- | -------- | --------- | ---------------------------------- |
| Approved        | Approved | Approved  | Eligible for next validation       |
| Rejected        | Any      | Any       | Reject or hold according to policy |
| Any             | Rejected | Any       | Reject or hold according to policy |
| Any             | Any      | Rejected  | Apply mandatory sanctions policy   |
| Review required | Any      | Any       | Manual review                      |
| Any             | Error    | Any       | Retry or operational hold          |
| Any             | Any      | Timeout   | Hold; do not transfer              |

A sanctions match may require a legally mandated freeze or other specific action rather than simply returning a generic rejection. The workflow must implement the applicable sanctions policy.

**Output:** `ComplianceApproved`, `ComplianceRejected`, `ManualReviewRequired`, or `ComplianceScreeningTimedOut`

### Phase 6: Perform Pre-Execution Validation

Immediately before blockchain execution, revalidate relevant conditions, including:

* Transfer instruction remains authorized and eligible.
* Required compliance decisions remain valid.
* No applicable hold or cancellation has been recorded.
* Token and network configuration remains valid.
* Required transfer limits and token restrictions remain satisfied.

For sensitive transfers, define whether compliance results expire and whether screening must be repeated when material transaction details change.

A material change to the source, destination, amount, token, or network must invalidate the previous approval unless the policy explicitly allows otherwise.

### Phase 7: Execute Solidity Token Transfer

Once the compliance gate approves the transfer, Camunda invokes the Blockchain Adapter service.

The adapter:

1. Loads the validated instruction.
2. Converts the amount to integer token base units.
3. Verifies the configured network and contract.
4. Selects the authorized transaction signer.
5. Prepares the contract call.
6. Applies nonce management and transaction-submission controls.
7. Submits the transaction through Web3j.
8. Persists the transaction hash and submission status.
9. Returns a submission result to the workflow.

Example contract interaction:

`transfer(to, amount)`

This standard ERC-20 operation is appropriate only when the contract and operating model support it. If compliance enforcement is implemented in the token contract, the adapter may instead call an authorized transfer method such as `transferFrom` or a token-specific compliant transfer function.

**Important:** An ERC-20 transfer does not automatically enforce off-chain KYC, AML, or sanctions decisions. The platform must enforce the compliance gate before submission, and any mandatory on-chain restrictions must also be enforced by the contract.

The contract must enforce the token's applicable balance, allowance, pause, role, and transfer restrictions.

For regulated assets, prefer a contract design that prevents unauthorized transfer paths rather than relying exclusively on the orchestration service.

**Output:** `BlockchainTransactionSubmitted`

### Phase 8: Await Blockchain Confirmation

After submission, Camunda waits for the transaction outcome.

The blockchain event listener or transaction-monitoring service:

1. Tracks the transaction hash.
2. Detects transaction inclusion.
3. Checks the receipt status.
4. Determines whether execution succeeded or reverted.
5. Applies the network's configured confirmation or finality policy.
6. Publishes a correlated transaction-result event.

Possible results:

* `CONFIRMED`
* `REVERTED`
* `PENDING`
* `REPLACED`
* `UNKNOWN`

A timeout must not automatically trigger a second transfer. The original transaction may still be pending or may have been mined while the response was lost.

If the transaction outcome is uncertain, the workflow must reconcile the existing transaction before deciding whether resubmission is safe.

**Output:** `BlockchainTransactionConfirmed` or `BlockchainTransactionFailed`

### Phase 9: Reconciliation and Completion

After the required confirmation or finality condition is met:

1. Retrieve authoritative blockchain transaction data.
2. Verify the expected transaction and receipt.
3. Verify the relevant token transfer event and amount.
4. Verify source and destination addresses.
5. Update the transfer instruction.
6. Record the final transaction hash and outcome.
7. Publish `TokenTransferCompleted`.
8. Notify the authorized client or downstream system.

For a failed or reverted transaction, record the failure and initiate the configured retry, investigation, or compensation procedure.

Never report a successful transfer based only on a database update or a transaction-submission acknowledgment.

## 8. Camunda BPMN Process Outline

Start Event: Transfer Instruction Received
|
v
Service Task: Validate Instruction
|
v
Exclusive Gateway: Valid?
|
+-- No --> Record Rejection --> End
|
+-- Yes
|
v
Parallel Gateway: Start Screening
|
+--> Service Task: Request KYC
|         |
|         v
|    Intermediate Message Catch:
|    KycScreeningResult
|
+--> Service Task: Request AML
|         |
|         v
|    Intermediate Message Catch:
|    AmlScreeningResult
|
+--> Service Task: Request Sanctions
|
v
Intermediate Message Catch:
SanctionsScreeningResult

```
      |
      v
```

Parallel Join: Await Required Results
|
v
Service Task: Aggregate Compliance Decisions
|
v
Event-Based / Exclusive Decision:
Compliance Outcome
|
+-- Rejected --> Record Decision --> End
|
+-- Manual Review --> User Task
|                       |
|                       v
|                  Review Decision
|
+-- Error / Timeout --> Retry or Hold
|
+-- Approved
|
v
Service Task: Revalidate Transfer
|
v
Service Task: Submit Solidity Transfer
|
v
Receive Transaction Outcome
|
v
Service Task: Verify Finality
|
v
Service Task: Reconcile Transfer
|
v
Update Final Status
|
v
Publish Completion Event
|
v
End Event

The BPMN model must define error paths, message-correlation rules, timeout handling, and cleanup of outstanding subscriptions. Parallel screening branches should converge only when the required results have been collected or the workflow has transitioned to an explicit failure or hold path.

## 9. Transfer State Machine

RECEIVED
|
v
VALIDATING
|
v
SCREENING_PENDING
|
+--> COMPLIANCE_REJECTED
|
+--> MANUAL_REVIEW
|
+--> COMPLIANCE_HOLD
|
v
COMPLIANCE_APPROVED
|
v
READY_FOR_EXECUTION
|
v
TRANSACTION_SUBMITTED
|
v
TRANSACTION_PENDING
|
+--> TRANSACTION_REVERTED
|
+--> TRANSACTION_UNKNOWN
|
v
TRANSACTION_CONFIRMED
|
v
RECONCILIATION_PENDING
|
v
COMPLETED

The state machine is illustrative. Implement explicit transition rules so that a late or duplicate message cannot move a terminal or held transfer into an invalid state.

## 10. Functional Requirements

**FR-001: Create Transfer Instruction**

The system shall create a uniquely identified transfer instruction containing the source wallet, destination wallet, token, amount, network, and authorized initiator.

**FR-002: Validate Transfer**

The system shall validate the instruction and required wallet, token, and authorization conditions before requesting compliance screening.

**FR-003: Initiate Compliance Screening**

The system shall initiate all mandatory KYC, AML, and sanctions screening requests.

**FR-004: Receive Asynchronous Results**

The system shall accept authenticated, correlated, and validated screening results from the designated providers.

**FR-005: Deduplicate Messages**

The system shall prevent duplicate screening messages from creating duplicate decisions or workflow side effects.

**FR-006: Aggregate Decisions**

The system shall apply the configured compliance policy and require all mandatory approvals before proceeding.

**FR-007: Enforce Timeouts**

The system shall place unresolved screening requests on hold or follow an explicitly configured retry or escalation policy.

**FR-008: Execute Token Transfer**

The system shall invoke the configured Solidity token contract only after the required compliance and transfer validations succeed.

**FR-009: Track Blockchain Outcome**

The system shall track the transaction hash and determine the transaction execution and confirmation outcome.

**FR-010: Reconcile Transfer**

The system shall reconcile the confirmed blockchain result against the original transfer instruction.

**FR-011: Provide Auditability**

The system shall record relevant workflow transitions, screening decisions, transaction references, and final outcomes.

**FR-012: Enforce Idempotency**

The system shall prevent duplicate instructions and duplicate processing from causing unintended token movement.

## 11. Non-Functional Requirements

* Security: authenticate and authorize all protected operations.
* Compliance: fail closed when mandatory screening is unresolved.
* Reliability: recover from transient service and messaging failures.
* Consistency: maintain traceable instruction and transaction states.
* Observability: correlate API requests, workflow instances, screening messages, and transaction hashes.
* Auditability: preserve compliance decisions and final transaction outcomes.
* Scalability: support concurrent transfers and asynchronous screening.
* Privacy: minimize the distribution and retention of sensitive identity information.
* Resilience: support retries without duplicating financial side effects.
* Recoverability: reconcile transactions after service restarts and lost responses.

## 12. Security Requirements

* Authenticate the transfer initiator.
* Authorize the initiator to act for the source wallet.
* Authenticate and validate external screening-provider messages.
* Use unique screening request IDs and correlation identifiers.
* Protect credentials and transaction-signing keys.
* Never expose private keys through application logs or workflow variables.
* Validate contract addresses, chain IDs, and token configuration.
* Reject invalid, expired, or inconsistent compliance decisions.
* Prevent unauthorized transfer execution and replay.
* Enforce applicable token restrictions within the smart contract.
* Restrict manual-review and administrative actions using RBAC.
* Audit security-sensitive actions and decisions.

## 13. Error Handling and Recovery

### Screening Provider Unavailable

Retry according to a bounded retry policy with backoff. If the result remains unavailable, place the transfer on hold and escalate when required.

### Duplicate Screening Result

Deduplicate using the provider, screening request ID, and message ID. Do not reapply a completed decision or advance the workflow twice.

### Late Screening Result

Associate the result with the original request and apply the defined late-response policy. A late approval must not automatically release a transfer already rejected, cancelled, or placed under a legally required hold.

### Blockchain RPC Failure

Determine whether the transaction was submitted before retrying. Query the existing transaction and reconcile its status to avoid duplicate transfers.

### Transaction Reverted

Record the transaction hash and failure reason where available. Do not mark the transfer complete.

### Workflow Restart

Recover from persisted workflow state and transaction records. Do not assume that an interrupted activity had no external side effects.

### Reconciliation Mismatch

Place the instruction in an investigation state, raise an operational alert, and prevent the system from reporting a successful transfer until the discrepancy is resolved.

## 14. Acceptance Criteria

### AC-001: Successful Token Movement

Given a valid transfer instruction and eligible wallets, when all mandatory compliance checks approve, then the workflow submits the authorized smart-contract transfer and marks it complete only after the required blockchain confirmation and reconciliation conditions are satisfied.

### AC-002: KYC Rejection

Given a transfer with a rejected KYC decision, when the workflow aggregates the screening results, then no blockchain transfer is submitted.

### AC-003: AML Rejection

Given a transfer with a rejected AML decision, when the decision is processed, then the workflow follows the configured rejection or hold policy and does not submit the transfer.

### AC-004: Sanctions Match

Given a sanctions result requiring rejection, restriction, or a freeze, when the result is received, then the applicable sanctions policy is enforced and no prohibited transfer is submitted.

### AC-005: Asynchronous Message Arrival

Given screening results that arrive in any order, when all mandatory results have been received and validated, then the workflow evaluates the combined decision correctly.

### AC-006: Missing Screening Result

Given a mandatory screening result that does not arrive before the configured timeout, then the transfer remains on hold or enters the defined escalation path and is not treated as approved.

### AC-007: Duplicate Screening Message

Given the same screening result delivered multiple times, when the messages are processed, then only one logical result is applied.

### AC-008: Invalid Message Correlation

Given a result message with an unknown or mismatched screening request ID, then the message is rejected or quarantined and cannot authorize another transfer.

### AC-009: Unauthorized Initiator

Given a caller who is not authorized to initiate a transfer from the source wallet, then the request is rejected before blockchain execution.

### AC-010: Blockchain Revert

Given a submitted transaction that reverts, then the workflow records the failure and does not mark the transfer complete.

### AC-011: Unknown Transaction Outcome

Given an RPC timeout after a potentially successful submission, then the workflow reconciles the original transaction before considering any resubmission.

### AC-012: Duplicate Transfer Instruction

Given repeated requests with the same idempotency key and equivalent instruction data, then the system returns the existing instruction or its current status without initiating a second logical transfer.

### AC-013: Changed Instruction

Given a repeated idempotency key with materially different transfer data, then the system rejects the conflicting request.

### AC-014: Reconciliation

Given a transaction that satisfies the configured finality condition, then the system verifies the expected token transfer and updates the instruction to `COMPLETED` only if reconciliation succeeds.

### AC-015: Audit Trail

Given a transfer that succeeds, fails, or enters review, then its material workflow transitions, screening decisions, and blockchain references are traceable through the instruction ID.

## 15. Definition of Done

* BPMN workflow is implemented and version-controlled.
* Transfer instruction API and persistence are implemented.
* KYC, AML, and sanctions request/response contracts are defined.
* Asynchronous message correlation and deduplication are implemented.
* Compliance decision aggregation and timeout paths are tested.
* Blockchain adapter and Solidity integration are implemented.
* Transaction monitoring and finality checks are implemented.
* Idempotency and unknown-outcome recovery are tested.
* Security and audit requirements are implemented.
* Automated unit, integration, and end-to-end tests pass.
* Operational alerts, recovery procedures, and workflow documentation are available.


Sequence of Stories:
| Order | Story ID   | Story                                                       | Primary component            |
| ----: | ---------  | ----------------------------------------------------------- | ---------------------------- |
|     1 | Epic-02.01 | Create transfer instruction API and database model          | Spring Boot / MongoDB     |
|     2 | Epic-02.02 | Validate wallet addresses and transfer eligibility          | Token Movement Service       |
|     3 | Epic-02.03 | Create Camunda token movement BPMN workflow                 | Camunda 8                    |
|     4 | Epic-02.04 | Initiate KYC screening and correlate results                | KYC Adapter / Camunda        |
|     5 | Epic-02.05 | Initiate AML screening and correlate results                | AML Adapter / Camunda        |
|     6 | Epic-02.06 | Initiate sanctions screening and correlate results          | Sanctions Adapter / Camunda  |
|     7 | Epic-02.07 | Implement authenticated screening-message ingestion         | Spring Boot / Kafka          |
|     8 | Epic-02.08 | Aggregate compliance decisions and enforce timeout policies | Camunda / Compliance Service |
|     9 | Epic-02.09 | Implement pre-execution compliance and transfer validation  | Compliance Service           |
|    10 | Epic-02.10 | Implement Web3j blockchain adapter                          | Spring Boot / Web3j          |
|    11 | Epic-02.11 | Implement and test Solidity transfer restrictions           | Solidity                     |
|    12 | Epic-02.12 | Track blockchain transaction status and finality            | Blockchain Listener          |
|    13 | Epic-02.13 | Implement reconciliation and final transfer status          | Reconciliation Service       |
|    14 | Epic-02.14 | Implement idempotency, retries, and recovery                | Application / Workflow       |
|    15 | Epic-02.15 | Implement audit trail, monitoring, and alerts               | Platform / Operations        |
|    16 | Epic-02.16 | Complete end-to-end transfer security testing               | QA / Security                |
