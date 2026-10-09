---
title: 'Implement Epic 0 infrastructure and delivery foundation'
type: 'feature'
ticket: 'Story-0.6-to-Story-0.16'
created: '2026-10-07'
status: 'built'
baseline_revision: '213a75500379a52e36ae56fdd18251f2e02ea783'
route: 'full'
route_source: 'auto'
review: 'thorough'
review_source: 'auto'
lenses_ran: [blind-hunter, edge-case-hunter, verification-gap, intent-alignment]
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture.md'
  - '{project-root}/docs/architecture/tech-stack.md'
  - '{project-root}/docs/architecture/source-tree.md'
  - '{project-root}/README.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Epic 0 stories 0.6–0.16 call for a usable local database, messaging, chain, smart-contract, operations, test, security, CI, and onboarding foundation; the repository has partial infrastructure definitions but these are not consistently configured, tested, or documented.

**Approach:** Complete the stories in sequence using the existing Gradle modules, Mongo-oriented application, Docker Compose services, Solidity sources, and GitHub Actions workflow. Add reproducible local setup, meaningful automated checks, and instructions validated against the actual project.

**Decision:** MongoDB is the authoritative database for Story 0.6 and this foundation. This follows the existing application, persistence module, Compose service, and Story 0.6 acceptance criteria; the Epic 0 PostgreSQL reference is inconsistent and will not be implemented as a second database.

## Boundaries & Constraints

**Always:** Preserve every pre-existing staged and unstaged change, especially user-owned Epic/story documents and Story 0.5 changes; do not modify Epic 0/story files. Keep secrets out of source, logs, and artifacts. Keep default Java tests isolated from optional external services, but provide explicit commands for integration layers.

**Never:** Implement full business-domain features beyond infrastructure/schema scaffolding; change unrelated work or Git history; claim infrastructure/test layers work without a repeatable verification path.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Database unavailable | Application starts while configured DB is down | No false-ready health; startup or operations expose the failure clearly and safely | Bounded retry/fail-fast behavior; no credentials in logs |
| Kafka/EVM unavailable | Local service stopped or invalid RPC/broker address | Connection failure is explicit; optional app startup and tests remain controllable | Retry/report errors without swallowing them or logging secret-bearing URIs |
| New developer setup | Clean checkout with documented prerequisites and Docker | Documented Compose, build, test, contract, and teardown commands run reproducibly | Troubleshooting identifies common missing tools, ports, and configuration |

</frozen-after-approval>

## Code Map

- `modules/contracts/src/main/resources/application*.yaml` and `modules/contracts/src/main/java/ai/generalcontracts/startup/config/` — current Spring profiles and Mongo client configuration from Story 0.5; preserve and extend these.
- `modules/persistence-mongo/`, `modules/chain-adapter/`, `modules/chain-listener/`, `modules/common-observability/`, `modules/app-bootstrap/` — intended integration modules; mostly placeholders.
- `deployment/docker-compose.yml`, `deployment/mongo-init.js`, `deployment/init-replica-set.sh` — Mongo replica set, Kafka/kraft, Zeebe/Elasticsearch, Anvil; inspect/fix relative mounts and add required UI/readiness.
- `modules/solidity-contract/src/main/solidity/` — existing five contracts; no configured compiler/test/deployment project.
- `gradle/libs.versions.toml`, `build.gradle`, `modules/*/build.gradle.kts`, `settings.gradle.kts` — Java 21 multi-module build; dependencies currently inherited broadly.
- `.github/workflows/ci.yml` — existing GitHub Actions Gradle check; add required tests/scans/artifacts and supported branch triggers.
- `modules/contracts/src/test/`, `tests/README.md` — current Java test layer and index; no integrated-contract/E2E or Solidity suites.
- `README.md`, `docs/security.md`, `docs/data-model.md` — onboarding currently needs verification; security doc is empty; data model depicts conceptual architecture.
- `_bmad-output/initiative-bmad-generated/deferred-work.md` — contains Story 0.6–0.16 deferred entries and unrelated work; update only the entries completed here.
- `docs/epics/epic-00-setup.md` and `docs/stories/epic-00-stories/Story-0.6.md`–`Story-0.16.md` — requirements only; do not edit these user-owned files.

## Tasks & Acceptance

**Execution:**
- [x] `modules/persistence-mongo/`, application config, dependency catalog, and Compose — implement the database choice and automatic infrastructure-level migrations/collections, local connectivity, and safe unavailable-service behavior; do not build full domain models (0.6).
- [x] Compose, Kafka module/config/tests — establish the existing broker, required named topics, JSON serialization, producer/consumer round-trip, and bounded retry/error logging (0.7).
- [x] Compose, chain config, and chain adapter — establish Anvil RPC, configurable chain ID, funded dev accounts, block production, and app connectivity (0.8).
- [x] `modules/solidity-contract/`, npm/Foundry configuration, and contract tests/scripts — compile existing Solidity, test a sample contract, and deploy it to local Anvil reproducibly (0.9).
- [x] `modules/chain-adapter/` and tests — configure externalized Web3j RPC access, query chain ID/latest block, and verify unreachable endpoint handling (0.10).
- [x] `deployment/docker-compose.yml` and related scripts — provide a healthy local stack including app, database, Kafka, Kafka UI, and EVM, with clean-start instructions (0.11).
- [x] `modules/common-observability/`, contracts config, and tests — expose health/metrics and propagate correlation/request/transaction identifiers; verify contextual error logging excludes secrets (0.12).
- [x] `docs/security.md`, build/npm/contract tooling, workflow — document assumptions and configure secret, dependency, container, and Solidity checks plus input validation/auth/TLS foundations appropriate to this platform (0.13).
- [x] Gradle/Solidity test configuration and `tests/README.md` — provide runnable unit, integration, API, end-to-end, and contract-test layers, with external-service requirements explicit (0.14).
- [x] `.github/workflows/ci.yml` — run build, applicable test layers, security scans, and container/package artifact generation on pushes/PRs; required failures fail CI (0.15).
- [x] `README.md` — provide all 13 requested onboarding sections with tested prerequisites, local/Docker, database, Kafka, blockchain, Solidity, test, build, deployment, and troubleshooting commands (0.16).
- [x] `_bmad-output/initiative-bmad-generated/deferred-work.md` — remove only the Story 0.6–0.16 entries completed by this plan; preserve unrelated deferred work.

**Acceptance Criteria:**

- Given the approved database technology and an available local service, when application setup runs, then infrastructure collections/tables are initialized automatically; when unavailable, failure is surfaced safely (0.6).
- Given Kafka is running, when a supported event is produced, then a consumer receives the serialized event and configured failure retry/logging is observable (0.7).
- Given local EVM Compose starts, when application and RPC clients connect, then chain ID, funded accounts, and generated blocks are accessible (0.8).
- Given existing Solidity contracts, when the documented build/test/deploy commands run, then compilation and tests pass and a sample deploys to local EVM (0.9).
- Given reachable and unreachable RPC endpoints, when Web3j integration is exercised, then chain ID/latest block are returned or a clear non-sensitive connection failure is reported (0.10).
- Given Docker and documented prerequisites on a clean checkout, when the Compose setup is run, then app and required local dependencies become healthy and usable (0.11).
- Given application requests and Actuator endpoints, when health, metrics, and correlation behavior are exercised, then useful identifiers/context are available and sensitive data is excluded (0.12).
- Given source and dependency changes, when documented security checks run, then secret/dependency/container/contract issues are detectable and platform security assumptions are documented (0.13).
- Given each configured test-layer command, when run in its documented environment, then unit, integration, API, end-to-end, and contract tests execute and report failures (0.14).
- Given supported pushes and pull requests, when CI runs, then build, tests, security scans, and artifact creation execute; failures produce a failed check (0.15).
- Given a new developer follows `README.md`, when documented setup/build/test/deployment steps are followed, then the local stack can be started and troubleshooting guidance is available (0.16).

## Implementation Notes

## Plan Change Log

## Review Triage Log

| Lens finding | Verdict / route | Evidence |
|---|---|---|
| Blind Hunter: readiness endpoint is unavailable outside Kubernetes | false | The Compose E2E check reached `/actuator/health/readiness`; probes are now explicitly enabled and covered by `ApplicationStartupTest`. |
| Blind Hunter: Hardhat `revert` matcher is invalid | false | The configured Hardhat Solidity tests ran successfully with both matcher assertions. |
| Blind Hunter: README starts Compose app and native app together | patch | Local setup now starts only MongoDB, Kafka, and Anvil dependencies before `bootRun`. |
| Blind Hunter: authenticated clients cannot obtain a CSRF token | patch | Added authenticated `GET /api/security/csrf`, documented cookie/header use, and tested fetch-and-submit behavior. |
| Blind Hunter: domain event topics lack corresponding DLTs | patch | Added the four missing domain DLT topics and covered them in topic tests and smoke checks. |
| Blind Hunter: Docker image runs as root | patch | Runtime image now uses UID/GID 10001; the full Compose smoke test starts it successfully. |
| Blind Hunter: production permits `SIGNER_MODE=local-dev-only` | false | The setting has no signer implementation or runtime consumer; production defaults to `vault` and requires a key reference. |
| Blind Hunter: five-second Mongo read timeout may reject slow operations | defer | It is a shared Story 0.5 client setting; workload latency requirements are undefined, so it is recorded for separate tuning. |
| Blind Hunter: migration marker skips checks after V1 | false | The marker is a versioned migration guard, not a drift-repair mechanism; initial migration and index constraints are tested. |
| Blind Hunter: all GitHub Actions should be SHA-pinned | defer | Trivy is pinned; broader official-action pinning requires a repository policy and is recorded separately. |
| Blind Hunter: CI removed executable-bit setup for `gradlew` | patch | Restored `chmod +x gradlew` before direct Gradle invocation; the committed wrapper mode is 100644. |
| Blind Hunter: BSON `Document` cannot encode `Instant` migration time | false | The earlier clean Compose startup completed with the `Instant` marker; the stored value now uses BSON-native `Date`. |
| Blind Hunter: explicit Mongo database overrides URI database | false | `MONGODB_DATABASE` is explicitly configured separately in application profiles and documented as an independent setting. |
| Blind Hunter: non-root container user is missing | patch | Same root-user issue as the earlier finding; the runtime stage now sets UID/GID 10001. |
| Blind Hunter: some domain topics lack DLTs | patch | Same DLT omission as above; each declared domain event topic now has a matching DLT. |
| Blind Hunter: fixed E2E project name can tear down another stack | patch | E2E uses a per-process Compose project name and no longer tears down a fixed project before startup. |
| Blind Hunter: RPC exception cause is discarded | false | Cause omission keeps endpoint details out of propagated exception/log output; tests assert the sanitized message does not expose the URL. |
| Blind Hunter: request ID is not propagated | patch | Added distinct `X-Request-Id` handling in MDC, responses, Kafka headers, consumer logs, and tests. |
| Blind Hunter: CI stopped uploading JaCoCo reports | patch | Restored the JaCoCo XML artifact upload with `if-no-files-found: ignore`. |
| Blind Hunter: Compose readiness check lacks enabled probes | false | Probes are explicitly enabled and readiness is verified in unit/startup and E2E checks. |
| Blind Hunter: Mongo migration marker uses unsupported timestamp type | false | Mongo startup succeeded with the prior value; current implementation stores a BSON-native date. |
| Blind Hunter: configured Mongo database always overrides URI path | false | Application profiles intentionally configure `MONGODB_DATABASE` independently; URI and database are both explicit configuration inputs. |
| Blind Hunter: chain RPC failure loses diagnostic cause | false | Propagating raw transport causes risks leaking configured RPC URLs; the public exception stays sanitized and tests cover that contract. |
| Blind Hunter: concurrent instances can race while building identical indexes | false | Both instances use the same idempotent index definitions; collection/marker races are handled and covered by initializer tests. |
| Edge Case Hunter: fixed Compose project cleanup can remove an existing stack | patch | Same isolation issue as above; dynamic project name and project-owned cleanup prevent targeting another stack. |
| Edge Case Hunter: required Mongo credentials can be partially supplied | patch | Customizer now rejects username/password asymmetry and has a focused test. |
| Edge Case Hunter: zero RPC timeout disables bounded calls | patch | RPC configuration rejects zero and negative values with a focused test. |
| Edge Case Hunter: null latest-block result can produce successful incomplete status | false | Web3j rejects a JSON `null` block response with a decoding exception; the client converts it to a sanitized connection failure, covered by integration test. |
| Edge Case Hunter: DLT partition may not exist | false | Source and DLT topics are provisioned with one partition; the active consumer's `.dlt` destination exists at partition zero. |
| Edge Case Hunter: producer failure logs accept control characters | patch | Topic and event ID are sanitized and bounded before logging; a log-capture test covers injected line breaks. |
| Edge Case Hunter: consumer logs allow Unicode line separators | patch | Consumer sanitization now replaces control, line-separator, and paragraph-separator characters; covered by test. |
| Edge Case Hunter: request ID is not propagated | patch | Same request-ID gap as above; response, MDC, Kafka, and test paths now carry it. |
| Edge Case Hunter: coverage artifact upload was removed | patch | Restored the prior JaCoCo XML artifact path. |
| Edge Case Hunter: index keys/uniqueness lack verification | patch | Initializer test now asserts eventId/messageId unique indexes and the publishedAt non-unique index. |
| Verification Gap: duplicate-index constraints can regress undetected | patch | Same index coverage gap; focused initializer test observes each index key and uniqueness option. |
| Intent Alignment: changed Epic/story files violate the frozen boundary | false | Those files were already staged/modified before this implementation and were preserved; this work did not edit them. |
| Intent Alignment: broad acceptance is not exercised by the test surface | false | The reported funded-account and Kafka UI gaps are now exercised by the end-to-end smoke test; tests cover Mongo/Kafka/RPC and the documented checks. |
| Prior review: local binding does not support container connectivity | patch | Local profile binds loopback while Compose overrides the container bind address; the Compose health and RPC checks passed. |
| Prior review: Mongo collection and migration marker creation can race | patch | Concurrent collection creation and duplicate marker insertion are handled, with dedicated initializer tests. |
| Prior review: dead-letter routing was not covered by the direct `handleOne` test | patch | Replaced the invalid retry entry-point test with a direct recoverer destination test and exercised the configured DLT topic in E2E. |
| Prior review: Docker build context may include environment and key files | patch | `.dockerignore` excludes environment files, private keys, certificates, and secret directories. |
| Prior review: README repeats profile instructions inconsistently | patch | Duplicate profile instructions were consolidated and local setup now separates dependency services from the Compose app. |
| Prior review: correlation and transaction context is not propagated consistently | patch | Request, correlation, and transaction identifiers now flow through filter/MDC, response, Kafka headers, and consumers, with tests. |

## Design Notes

Reuse Compose's existing Confluent Kafka and Anvil services and GitHub Actions CI. Keep Solidity code in its existing Gradle module rather than duplicating contracts into a second source tree. Implement dependencies in story order; Web3j follows the local EVM, while CI and README validate the completed stack.

## Verification

**Commands:**
- `.\gradlew.bat --no-daemon test integrationTest apiTest check :modules:contracts:bootJar` — passed, including unit, integration, API, quality, and packaging tasks.
- `docker compose -f deployment/docker-compose.yml config --quiet` — passed.
- `bash tests/e2e-smoke.sh` — passed; verifies readiness, chain status, funded Anvil account, Kafka UI, Mongo collections, DLT topics, contract deployment, and Kafka consumption.
- `npm run contracts:compile`, `npm run contracts:test`, and `npm run contracts:lint` — passed; contract tests report two passing tests.
- `npm audit --audit-level=high` — passed with no reported vulnerabilities.
- `.\gradlew.bat --no-daemon :modules:chain-adapter:integrationTest` — passed after the final RPC client edit.
- `git diff --check` and `git diff --cached --check` — passed.
- GitHub Actions scans and artifact uploads are configured in `.github/workflows/ci.yml`; GitHub-hosted execution was not available locally.

**Manual checks (if no CLI):**
- Confirm no credentials are committed or logged, the security assumptions match actual controls, and README commands are executable from a clean checkout.
