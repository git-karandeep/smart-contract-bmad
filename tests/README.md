# Test organization

Tests are maintained with their owning Gradle module under
`modules/<module>/src/test/`; do not duplicate module tests in this directory.
For example, the current `contracts` test source is under
`modules/contracts/src/test/`.

Use the module's configured Gradle test task to run its tests. When documenting
acceptance criteria, link the specific test class where one exists. If no
automated test covers a documentation or repository-structure criterion, state
that verification is manual rather than implying an unrelated test covers it.
