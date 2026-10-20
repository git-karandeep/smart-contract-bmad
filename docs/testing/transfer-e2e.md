# Transfer lifecycle end-to-end tests (Story 2.16)

Run: `.\gradlew.bat :modules:process-workers:test` (all modules: `.\gradlew.bat test`).

The `endToEnd*` tests in `TransferLifecycleJobHandlersTest` drive the real workflow handlers
(screening aggregation, pre-execution gate, submission, reconciliation) with deterministic in-process
fakes for providers, persistence, the submission port and the chain reconciliation port. No production
credentials, networks, Camunda, Mongo or Kafka are required, so they are repeatable in CI.

Covered: single completion, screening rejection/review/error, revert/replaced/unknown/mismatch, duplicate
replay and resubmission, and absence of wallet or signing data in workflow outputs. Assertion messages
carry only workflow outputs, never secrets.

Open decision: whether CI must also exercise a deployed Camunda/Mongo/Kafka stack. Until confirmed,
those boundaries are covered by their own module tests.
