---
title: 'Configure application profiles'
type: 'chore'
ticket: 'Story-0.5'
created: '2026-10-07'
status: 'built'
baseline_revision: '213a75500379a52e36ae56fdd18251f2e02ea783'
route: 'full'
route_source: 'auto'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture.md'
  - '{project-root}/docs/architecture/tech-stack.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The contracts application has a single configuration with embedded database connection settings and no distinct local, test, or production profiles.

**Approach:** Separate common settings from local, test, and production configuration; externalize sensitive connection values; and verify profile selection and environment override behavior without contacting external services.

## Boundaries & Constraints

**Always:** Preserve existing staged/unstaged edits, especially user-edited Epic 0 and story files. Do not expose existing credential values. Keep the existing application startup behavior and isolate profile tests from live infrastructure.

**Never:** Implement database migration/infrastructure or later Epic 0 stories in this change; commit unrelated files or change Git history.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Local/test profile | Profile selected with no production secrets | Uses safe local/test endpoints and starts without contacting a remote database | Invalid profile/configuration fails with a clear, non-sensitive message |
| Production profile | Production profile plus environment-provided connection settings | Required sensitive settings come from environment; environment values override defaults | Missing required settings fail clearly without disclosing values |

</frozen-after-approval>

## Code Map

- `modules/contracts/src/main/resources/application.yaml` — shared config currently includes database endpoint/auth settings; remove embedded secrets and retain common safe defaults.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/config/MongoClientConfig.java` — consumes Spring `MongoProperties` and must remain compatible with profile values.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/config/MongoPropertiesClientSettingsBuilderCustomizer.java` — applies URI/host/auth/TLS settings and reads certificate properties.
- `modules/contracts/src/test/java/ai/generalcontracts/startup/ApplicationStartupTest.java` — existing isolated startup smoke test; run using test profile and preserve non-routable DB isolation.
- `modules/contracts/src/test/` — add configuration-focused tests for profile selection, environment precedence, and required production settings.
- `deployment/docker-compose.yml` — local Mongo credentials/endpoints are defined for development; configuration must align without copying them into production defaults.

## Tasks & Acceptance

**Execution:**
- [ ] `modules/contracts/src/main/resources/application.yaml` and `application-{local,test,prod}.yaml` — split common/profile-specific configuration; externalize DB credentials, connection URI, chain signer references, and other sensitive settings through environment-backed properties.
- [ ] `modules/contracts/src/test/java/ai/generalcontracts/startup/` — test local/test profile loading, production secret absence/required values, environment override precedence, and isolated application startup.
- [ ] `modules/contracts/src/main/java/ai/generalcontracts/startup/config/` — update configuration binding only as needed so profiles preserve Mongo URI, TLS, and credential behavior without defaults that connect to a remote service.
- [ ] `README.md` — document profile selection and required environment variables without using real credentials.

**Acceptance Criteria:**

- Given the local profile, when the application starts with documented local settings, then it loads and uses local service defaults.
- Given the test profile, when tests start the application, then it loads without external infrastructure or production secrets.
- Given the production profile, when configuration is inspected, then no credentials or private keys are embedded and required sensitive settings are supplied externally.
- Given an environment variable overrides a profile default, when the application binds configuration, then the environment value is used.
- Given a required production setting is missing, when production configuration loads, then startup reports the missing property without printing secret values.

## Implementation Notes

Story 0.5 was implemented as a configuration-only change scoped to the contracts application:
- `application.yaml` now keeps common safe defaults and environment overrides.
- `application-local.yaml`, `application-test.yaml`, and `application-prod.yaml` isolate profile-specific values.
- Embedded production-like database settings were removed and replaced with environment-based properties.
- `ProductionProfileValidator` enforces required production inputs for MongoDB URI, RPC URL, and signer key reference.
- `ProfileConfigurationTest` verifies profile defaults, environment override precedence, and fail-fast production validation without requiring live infrastructure.

## Plan Change Log
\n- `2026-10-07`: Implemented the profile split and production validation in the contracts module following the approved Story 0.5 plan. Remaining stories are deferred as separate workstreams per the split plan.

## Review Triage Log

## Design Notes

Keep the default/local configuration safe for developer use and make production requirements fail fast. Do not make unit or startup tests depend on the external Mongo endpoint currently represented in the base configuration.

## Verification

**Commands:**
- `.\gradlew.bat :modules:contracts:test --no-daemon` — expected: startup and profile tests pass without external services.
- `.\gradlew.bat test --no-daemon` — expected: all existing and added tests pass.
- `git diff --check` — expected: no whitespace errors and no edits to user-owned story/epic files.

**Manual checks (if no CLI):**
- Inspect all application profile files and confirm production secrets are absent and the documented environment-variable names match the bound settings.
