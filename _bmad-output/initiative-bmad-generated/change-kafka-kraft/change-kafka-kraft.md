# Sprint Change Proposal: Replace Local Kafka ZooKeeper with KRaft

**Status:** Approved; implementation completed  
**Change mode:** Incremental  
**Trigger:** Planned simplification of the local Kafka infrastructure

## 1. Issue Summary

The local Docker Compose Kafka broker currently depends on a separate ZooKeeper service. The requested simplification is to run the local broker in KRaft mode and remove ZooKeeper from the local application setup. The existing Kafka application contract is based on bootstrap servers and does not require changes when Kafka's metadata mode changes.

Evidence in the current setup:

- `deployment/docker-compose.yml` defines `zookeeper` using `confluentinc/cp-zookeeper:7.9.0`; Kafka sets `KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181` and waits for ZooKeeper health.
- The Compose model declares `kafka_data`, `zookeeper_data`, and `zookeeper_log`.
- `modules/contracts/src/main/resources/application.yaml` configures Kafka through `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`).
- `tests/e2e-smoke.sh` exercises the broker and application event path, but does not assert KRaft quorum availability or ZooKeeper absence.

This is a local infrastructure simplification, not a change to business messaging semantics or external Kafka deployments.

## 2. Impact Analysis

### Epic impact

- **Epic 0 — Setup/foundation:** remains viable and complete. No new epic, removal, resequencing, or priority change is needed.
- The completed Kafka and Compose stories are **Story 0.7** and **Story 0.11**. Their acceptance criteria need to record the KRaft topology and volume isolation.
- Other planned epics and dependencies are unaffected.

### Artifact impact

| Artifact | Impact |
|---|---|
| PRD | No change. Requirements specify Kafka event publishing, not its metadata mode. MVP remains achievable. |
| Stories 0.7 and 0.11 | Update acceptance criteria to require local KRaft, retain application connectivity/behavior, and establish a fresh data volume. |
| Architecture | Clarify that local Compose uses single-node KRaft; Kafka remains optional at the business architecture level and external deployments are unaffected. |
| Compose | Remove the local ZooKeeper service/dependency; configure Confluent Kafka for single-node KRaft and a new persistent volume. |
| E2E smoke and test documentation | Verify KRaft readiness and no ZooKeeper service; describe the resulting stack accurately. |
| README | Document local KRaft setup and legacy-volume behavior. |
| Application config and Kafka behavior | No change. Preserve bootstrap-server configuration, producers, consumers, topics, serialization, retry behavior, and endpoints. |
| UI/UX | Not applicable; no user interface or workflow changes. |

### Data and compatibility constraints

Create and use a new named volume, `kafka_kraft_data`. Do not mount, migrate, or delete the existing `kafka_data`, `zookeeper_data`, or `zookeeper_log` volumes. They remain untouched and unreferenced by the revised local stack. No automatic cleanup or migration is allowed.

Preserve the application bootstrap endpoints (`kafka:29092` from Compose services and `localhost:${KAFKA_HOST_PORT:-9092}` for host clients), Kafka UI port, topic names, and app-to-Kafka behavior.

## 3. Recommended Approach

**Selected path: Direct Adjustment.**

Adjust the completed acceptance criteria and the directly related Compose, architecture, smoke-test, and onboarding documentation. Keep the existing Epic 0 structure and application Kafka contract.

- **Effort:** Low; one focused infrastructure/documentation change.
- **Risk:** Low to medium. The principal risks are KRaft environment-variable or quorum misconfiguration and accidentally reusing legacy Kafka data. A fresh named volume, explicit quorum readiness check, and isolated E2E project address these risks.
- **Timeline:** No epic resequencing or MVP schedule change anticipated; implementation and verification fit within one development iteration.
- **Alternatives considered:** Rolling back completed Kafka/Compose stories adds work without simplifying the migration. MVP review is unnecessary because no PRD requirement is blocked or changed.

## 4. Detailed Change Proposals

All proposals below were individually approved during incremental review.

### Stories

**Story:** 0.7 — Establish Kafka Infrastructure  
**Section:** Acceptance Criteria

**OLD**

> Kafka starts locally  
> Application connects to Kafka  
> Producer works  
> Consumer works  
> Serialization is configured  
> Failed messages can be logged/retried

**NEW**

> Kafka starts locally in single-node KRaft mode with no ZooKeeper service.  
> Application connects using the existing bootstrap-server contract.  
> Producer and consumer behavior, serialization, topics, and failed-message logging/retry remain unchanged.  
> KRaft uses a new persistent volume; existing ZooKeeper-era Kafka and ZooKeeper volumes are left untouched and are not migrated or automatically deleted.

**Rationale:** Capture the infrastructure mode and protect existing local data while retaining Story 0.7's event-messaging behavior.

**Story:** 0.11 — Configure Docker Compose  
**Section:** Acceptance Criteria

**OLD**

> A clean machine can start the development environment with documented commands.

**NEW**

> A clean machine can start the documented development environment with a healthy single-node KRaft broker and no ZooKeeper service.  
> The application and Kafka UI remain reachable at their documented endpoints; application bootstrap configuration and Kafka messaging behavior are unchanged.  
> Compose uses a new KRaft data volume and does not migrate, reuse, or automatically delete existing Kafka/ZooKeeper-era volumes.  
> End-to-end smoke verification confirms KRaft quorum readiness, application connectivity, expected topics, and event consumption using an isolated Compose project.

**Rationale:** Define a reproducible Compose acceptance test and the safe data boundary for the completed infrastructure story.

### Architecture

**File:** `docs/architecture.md`  
**Sections:** 3, “Technology Stack”; 11.2, “Local docker-compose services”

**OLD**

> | Messaging (optional) | Kafka | Only if fan-out beyond Zeebe is needed |
>
> Zeebe (+ Operate/Tasklist), Elasticsearch/OpenSearch, MongoDB (single-node replica set), Anvil or Besu, optional Kafka, Prometheus/Grafana.

**NEW**

> Keep the optional Kafka technology-stack row, and add: “The local Compose Kafka broker runs as a single-node KRaft cluster; the application connects through bootstrap servers. This local choice does not constrain external Kafka deployments.”
>
> Update the local services description to identify optional Kafka as single-node KRaft and explicitly state that ZooKeeper is not part of the local stack.

**Rationale:** Distinguish a local deployment choice from business architecture and external deployment requirements.

### Compose infrastructure

**File:** `deployment/docker-compose.yml`  
**Sections:** `zookeeper`, `kafka`, and named `volumes`

**OLD**

> Compose starts a ZooKeeper service with `zookeeper_data` and `zookeeper_log`; Kafka sets `KAFKA_ZOOKEEPER_CONNECT`, waits for ZooKeeper health, and stores data in `kafka_data`.

**NEW**

> Remove the ZooKeeper service, its Kafka dependency, and ZooKeeper connection setting. Configure the Confluent Kafka 7.9.0 broker for single-node KRaft with a stable local cluster ID, broker/controller roles, node ID, controller listener and quorum voters, and the existing client listeners. Retain the single-replica topic/transaction settings and Kafka health check. Store broker data in a new `kafka_kraft_data` named volume mounted at `/var/lib/kafka/data`; do not reference or migrate the old Kafka/ZooKeeper volumes.
>
> Keep the app and Kafka UI dependent on Kafka health. Preserve `kafka:29092` for Compose clients and `localhost:${KAFKA_HOST_PORT:-9092}` for host clients. Do not change application configuration or Kafka application behavior.
>
> Preserve the existing Kafka UI host mapping by setting its server port to the mapped container port, without changing the published host port.

**Rationale:** Configure Kafka's metadata quorum without altering client-facing broker connectivity, and isolate the new broker data from legacy volumes.

### Smoke verification and developer documentation

**Files:** `tests/e2e-smoke.sh`, `tests/README.md`, and `README.md`

**OLD**

> The smoke test waits for Kafka readiness and exercises topics and event consumption, but does not assert ZooKeeper absence or KRaft quorum availability. Developer documentation explains local Kafka/bootstrap usage without documenting KRaft or the legacy-volume boundary.

**NEW**

> Extend E2E smoke verification to assert the isolated Compose project has no ZooKeeper service, verify KRaft quorum readiness using the Kafka broker's quorum tooling, and retain checks for app connectivity, expected topics, and event consumption. Keep cleanup limited to the smoke test's uniquely named Compose project and its volumes.
>
> Update `tests/README.md` to describe the smoke stack as using Kafka in KRaft mode. Update `README.md` local setup and Kafka/teardown guidance to identify local single-node KRaft, preserve the existing host/container bootstrap addresses and ports, and state that ZooKeeper-era volumes are not migrated or automatically removed. Any optional cleanup guidance must be explicit and manual; no setup or teardown command may delete legacy volumes implicitly.

**Rationale:** Make the migration invariant testable and prevent developer documentation or smoke cleanup from implying data migration or deletion.

## 5. Implementation Handoff

**Scope classification:** Minor — direct implementation by the Developer agent.

**Backlog changes:** Rescope the acceptance criteria of completed Stories 0.7 and 0.11 as described above. No stories are added or removed, and no resequencing is required.

**Implementation order:**

1. Update the story acceptance criteria and local architecture documentation.
2. Reconfigure Compose for KRaft and the isolated `kafka_kraft_data` volume.
3. Extend the smoke test and update setup/test documentation.
4. Verify Compose rendering, a fresh-volume KRaft startup and quorum, topic provisioning, app event consumption, and project-scoped cleanup. Confirm legacy volumes are neither mounted nor removed.

**Handoff recipient:** Developer agent, to implement only after approval of this complete proposal. Preserve all unrelated staged, unstaged, and untracked work already present in the repository, including current user changes to `README.md`, Compose, and `tests/e2e-smoke.sh`.

**Success criteria:**

- The default local Compose stack runs Kafka in single-node KRaft mode without ZooKeeper.
- Kafka UI and the application connect through the existing endpoints; existing topic, serialization, producer/consumer, and retry behavior is unchanged.
- E2E smoke verifies KRaft quorum readiness and event consumption.
- KRaft uses a new volume; no legacy Kafka/ZooKeeper data is reused, migrated, or automatically deleted.
- README and test documentation accurately describe startup, connectivity, and cleanup.

## Checklist status

- Trigger and evidence identified: **Done**
- Epic impact assessed: **Done** — Epic 0 remains viable; no new epic or resequencing.
- PRD and architecture impact assessed: **Done** — no PRD change; local architecture clarification proposed.
- UX impact assessed: **N/A**
- Deployment, tests, and documentation impact assessed: **Done**
- Path forward evaluated: **Done** — Direct Adjustment selected.
- Detailed incremental proposals: **Done** — all five proposal groups approved.
- Final proposal approval: **Approved by user**
- Implementation handoff: **Authorized — Developer agent, Minor scope**

## Workflow execution log

- Incremental proposals were presented and approved individually.
- User reviewed the complete proposal, chose **Continue**, and explicitly approved it for implementation handoff.
- Handoff recipient: Developer agent.
- The approved KRaft change was implemented and the isolated end-to-end smoke completed successfully.
- No commit was created so the repository's pre-existing staged and unstaged user changes remain untouched.
