# Epic: Standardized BPMN Design and Reusable Workflow Generator

## Epic Goal

Implement an automated BPMN workflow standardization engine that accepts `transfer-lifecycle-source.bpmn` as input, analyzes its process structure, and generates standardized, executable Camunda 8 BPMN models following enterprise workflow design best practices.

The engine must identify reusable business capabilities, extract them into independent BPMN processes, and replace the corresponding activities in the parent process with reusable Call Activities. The generated workflows must preserve the original process structure, business logic, sequence flows, message correlations, timeout handling, and error-handling semantics while introducing consistent retry policies, naming conventions, input/output mappings, and reusable process interfaces.

## Core Capabilities

### 1. BPMN Analysis and Standardization

* Parse and validate the input BPMN model.
* Identify service tasks, gateways, message events, timers, subprocesses, and reusable business capabilities.
* Apply standardized naming conventions, task definitions, process identifiers, and workflow design rules.
* Preserve existing business logic unless a change is explicitly required by a standardization rule.

### 2. Reusable Called Activity Generation

Automatically extract common compliance capabilities into independently deployable BPMN processes.

Required reusable processes:

* `kyc-screening-process.bpmn`
* `aml-screening-process.bpmn`
* `sanctions-screening-process.bpmn`

Each reusable process must encapsulate the relevant request, result correlation, timeout handling, retries, and outcome handling.

The parent `transfer-lifecycle-source.bpmn` must invoke these processes through Camunda 8 Call Activities, using explicitly defined process IDs and input/output mappings.

### 3. Retry and Failure-Handling Standards

* Apply consistent retry policies to service tasks.
* Distinguish transient technical failures from business rejections.
* Define bounded retries and appropriate backoff strategies.
* Handle screening timeouts without accidentally treating missing responses as approvals.
* Support incident handling and recovery for exhausted retries.
* Ensure payment submission is idempotent and does not blindly retry an operation with an unknown outcome.

### 4. Reusable Process Contracts

Each called process must define:

* A stable process ID and versioning strategy.
* Required input variables.
* Output variables and outcome codes.
* Correlation identifiers for asynchronous responses.
* Timeout and failure semantics.
* Retry and incident-handling behaviour.
* Clear ownership of variables and mappings.

### 5. Automated Generation and Validation

The engine must generate:

* A standardized parent BPMN process.
* Separate reusable BPMN files for KYC, AML, and sanctions screening.
* A machine-readable report identifying extrac
