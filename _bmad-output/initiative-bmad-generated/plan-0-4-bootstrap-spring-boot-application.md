---
title: 'Bootstrap the Spring Boot application'
type: 'chore'
ticket: 'Story-0.4'
created: '2026-10-07'
status: 'built'
baseline_revision: '4bc990a8a332d3404ba07ae536680f32001d7b6d'
route: 'oneshot'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: ['quick']
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent - do not modify unless human renegotiates">

## Intent

**Problem:** The contracts module has a Spring Boot entry point and Actuator dependency, but startup and `/actuator/health` are not demonstrated, graceful shutdown and basic logging are not configured explicitly, and several requested startup packages do not exist.

**Approach:** Complete the existing Spring Boot entry point configuration in the contracts module, expose the health endpoint, configure graceful shutdown and basic logging, retain Spring's external configuration overrides, add only the missing package markers, and verify a real HTTP health response in an isolated Spring Boot test.

**Always:** Preserve existing edits to Epic 0 and all story files; do not edit them. Keep current package names and existing Spring integrations working.

**Never:** Do not implement Story 0.5's environment-specific profile files or the separate database, Kafka, blockchain, Docker, observability, security-scan, CI, or documentation stories in this change.

</frozen-after-approval>

## Implementation Notes

One-shot is appropriate: the selected Story 0.4 is one cohesive application-bootstrap deliverable; remaining Epic 0 stories are recorded in the initiative deferred-work list.

- Reused the existing `GeneralContractApplication` entry point and Actuator dependency.
- Added explicit health exposure, environment-overridable server port/log level, and graceful shutdown settings in `application.yaml`.
- Added package markers only for missing Story 0.4 packages; existing controllers, integrations, and user-edited story files remain untouched.
- Added an HTTP smoke test for `/actuator/health` and shutdown/logging property checks. Its Mongo client URI is pinned to a non-routable local port and Mongo health is disabled so tests do not contact configured infrastructure.
- Quick review confirmed the test's isolated startup approach; a test that requires the live database would make verification depend on external infrastructure.

## Review Triage Log

- `defer` — Existing database authentication values in `modules/contracts/src/main/resources/application.yaml` predate this story and are outside Story 0.4; an audit/externalization follow-up was recorded for the database/security work.
- `false` — The test intentionally overrides external Mongo settings and disables the Mongo health contributor to verify HTTP startup without infrastructure. Requiring the checked-in database endpoint would make tests depend on external services; the application context and `/actuator/health` response are exercised directly.

## Verification

**Commands:**
- `.\gradlew.bat :modules:contracts:test --no-daemon` -- expected: Spring Boot starts in an isolated test configuration and the HTTP health endpoint returns `UP`.
- `.\gradlew.bat test --rerun-tasks --no-daemon` -- expected: root build succeeds and executes the contracts tests.
