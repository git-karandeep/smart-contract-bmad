---
title: 'Complete Story 0.3 Gradle Java project setup'
type: 'chore'
ticket: 'Story-0.3'
created: '2026-10-07'
status: 'built'
baseline_revision: '91ef0a12e9d77a94fc62509ad5392db1a6305141'
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

**Problem:** Story 0.3's Gradle wrapper, multi-module settings, root build, Java 21 declaration, and JUnit dependencies already exist and compile, but Java 21 is only configured on the root project, and `gradlew test` reports `NO-SOURCE` for every project because the only contracts test file is commented out.

**Approach:** Apply the existing Java 21 toolchain setting to each Gradle subproject and add a focused JUnit 5 unit test for the existing `Orders` model in the contracts module. Preserve the current wrapper, module layout, and dependency choices.

**Always:** Preserve the user's staged deletion of `modules/chain-listener/build.gradle` and staged IDE datasource files. Keep changes limited to Java toolchain consistency and an executable unit test.

**Never:** Do not refactor dependency management, change framework versions, modify the existing commented integration-test file, or restore/re-stage unrelated user changes.

</frozen-after-approval>

## Code Map

- `build.gradle` -- applies the Java plugin to each subproject and declares Java 21 only for the root project; configure subproject toolchains using the same version.
- `modules/contracts/build.gradle.kts` -- applies Java and inherits shared JUnit 5 dependencies and `useJUnitPlatform()` from the root build.
- `modules/contracts/src/main/java/ai/generalcontracts/startup/model/Orders.java` -- Lombok-backed model suitable for a dependency-free unit test.
- `modules/contracts/src/test/java/ai/generalcontracts/startup/GeneralContractApplicationTests.java` -- currently commented out; do not alter this environment-dependent integration-test draft.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `settings.gradle.kts` -- existing committed wrapper and multi-module build entry points.

## Tasks & Acceptance

**Execution:**
- [x] `build.gradle` -- Configure Java 21 toolchains for every subproject from the root's existing version -- ensure all modules use the declared runtime consistently.
- [x] `modules/contracts/src/test/java/ai/generalcontracts/startup/model/OrdersTest.java` -- Add JUnit 5 coverage for the model's generated accessors -- prove test discovery, dependency resolution, and test execution.

**Acceptance Criteria:**
- Given a Gradle build, when the root and subprojects are configured, then Java 21 is the toolchain for each Java project.
- Given the committed wrapper and multi-module settings, when a developer runs the contracts compile task, then the application module compiles successfully.
- Given the contracts unit test task, when Gradle executes `:modules:contracts:test`, then JUnit 5 discovers and passes the `Orders` model test rather than reporting `NO-SOURCE`.
- Given the root test task, when a developer runs `.\gradlew.bat test`, then Gradle completes successfully and executes the contracts unit test.

## Implementation Notes

One-shot is appropriate: implementation changes are limited to one root Gradle configuration and one focused test file.

- Defined one shared Java 21 version in `build.gradle` and applied its toolchain to the root and all Java subprojects.
- Added `OrdersTest`; Gradle reports one executed test with zero failures instead of `NO-SOURCE` for the contracts module.
- The root `build` and `test` tasks succeed. The pre-existing staged IDE datasource additions and `modules/chain-listener/build.gradle` deletion were left untouched.
- Quick review found no issues.

## Verification

**Commands:**
- `.\gradlew.bat :modules:contracts:compileJava :modules:contracts:compileTestJava --no-daemon` -- expected: successful Java 21 compilation.
- `.\gradlew.bat :modules:contracts:test --no-daemon` -- expected: the `OrdersTest` test executes and passes.
- `.\gradlew.bat test --no-daemon` -- expected: root build succeeds and discovers the contracts unit test.
