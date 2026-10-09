---
title: 'Replace local Kafka ZooKeeper with KRaft'
type: 'feature'
ticket: ''
created: '2026-10-08'
status: 'built'
baseline_revision: 'a21742d808ab7153e6551d722634de633ff71b4b'
route: 'oneshot'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/initiative-bmad-generated/change-kafka-kraft/change-kafka-kraft.md'
  - '{project-root}/docs/architecture.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Local Compose Kafka depends on ZooKeeper, adding an unnecessary service to the development stack.

**Approach:** Replace the local broker setup with a single-node Confluent Kafka 7.9.0 KRaft cluster, isolate its data in a new named volume, and document and verify the change without changing Kafka client behavior.

</frozen-after-approval>

## Code Map

- `deployment/docker-compose.yml` — currently defines ZooKeeper, the ZooKeeper-backed Kafka broker, app/UI health dependencies, and legacy named volumes. Preserve current user edits to published app/UI ports. Remove the local ZooKeeper service and dependency; configure Kafka using `CLUSTER_ID`, KRaft roles, node ID, controller listener/quorum, current client listeners, existing health check, and a new `kafka_kraft_data` volume. Do not reference or alter legacy volumes.
- `tests/e2e-smoke.sh` — uniquely names its Compose project and tears down only that project. Preserve the existing user-selected app host port. Add assertions for no ZooKeeper service and KRaft quorum status; retain topic and event-consumption checks.
- `docs/stories/epic-00-stories/Story-0.7.md`, `Story-0.11.md` — completed story acceptance criteria to rescope per the approved proposal.
- `docs/architecture.md` — Sections 3 and 11.2 describe optional Kafka and local Compose services; state the local-only KRaft choice.
- `README.md`, `tests/README.md` — local setup, Kafka, test and teardown instructions; preserve current user changes and document legacy-volume handling.
- `modules/contracts/src/main/resources/application.yaml` — Kafka bootstrap configuration is transport-mode agnostic; do not change it.
- `_bmad-output/initiative-bmad-generated/change-kafka-kraft/change-kafka-kraft.md` — approved scope and acceptance requirements; use as source of truth.

## Tasks & Acceptance

**Execution:**
- [x] `deployment/docker-compose.yml` — replace ZooKeeper mode with single-node KRaft and `kafka_kraft_data`; retain client listeners, service ports, health dependencies, and Kafka replication behavior while leaving old volumes untouched.
- [x] `tests/e2e-smoke.sh` — assert Compose has no ZooKeeper service and Kafka quorum has a leader; preserve isolated project cleanup and existing app, topic, and event checks.
- [x] `docs/stories/epic-00-stories/Story-0.7.md`, `Story-0.11.md` — add the approved KRaft and volume-isolation acceptance criteria.
- [x] `docs/architecture.md`, `README.md`, `tests/README.md` — describe the local KRaft setup and unchanged bootstrap endpoints; clarify that legacy volumes are neither migrated nor automatically removed.

**Acceptance Criteria:**
- Given a fresh Compose project, when the default stack starts, then Kafka is healthy in single-node KRaft mode, has no ZooKeeper service, and stores its data in `kafka_kraft_data`.
- Given the running local stack, when the app and Kafka UI connect, then their existing endpoints and bootstrap addresses work and application messaging behavior remains unchanged.
- Given the E2E smoke run, when it inspects Kafka and the event path, then quorum status reports a leader, expected topics exist, and the app consumes the smoke event.
- Given pre-existing Kafka/ZooKeeper-era named volumes, when the new stack starts and the isolated smoke project is cleaned up, then those legacy volumes are neither mounted, reused, migrated, nor deleted.
- Given the local setup and test documentation, when a developer follows it, then KRaft startup, bootstrap addresses, and safe legacy-volume handling are clear.

## Implementation Notes

- The first broker start exposed an advertised-listener validation error when the internal listener bound to `0.0.0.0`; use routable `kafka` service addresses for internal broker/controller listeners. The E2E run then confirmed quorum and app readiness, but exposed the pre-existing Kafka UI mapping to container port 8443 while the UI defaults to 8080. Set its Spring `SERVER_PORT` to 8443 to honor the existing user-edited mapping rather than replacing that change.
- The isolated end-to-end smoke now passes through KRaft quorum validation, Kafka UI readiness, topic checks, contract deployment, and application event consumption. The smoke cleanup removed only its uniquely named Compose project's containers, network, and volumes.
- Compose rendering, KRaft environment/volume assertions, Bash syntax, and whitespace checks pass. The Docker volume listing after smoke showed no named legacy Kafka/ZooKeeper volumes or leftover named smoke volumes.
- No commit was created because the worktree already contains unrelated staged, unstaged, and untracked user changes; those changes remain untouched.

## Plan Change Log

## Review Triage Log

## Verification

**Commands:**
- `docker compose -f deployment/docker-compose.yml config` — expected: valid service/volume model, no ZooKeeper service, and only `kafka_kraft_data` for local Kafka persistence.
- `bash tests/e2e-smoke.sh` — expected: fresh isolated KRaft stack reaches quorum, expected topics are present, the app consumes the event, and cleanup is project-scoped.
