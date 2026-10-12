Story 1: Platform Foundation
Description: Set up the foundational structure for the project, including Gradle multi-module scaffolding, conventions, CI pipeline, and a local docker-compose environment.  Tasks:  
Create Gradle multi-module structure as per source-tree.md.
Implement convention plugins in build-logic.
Configure settings.gradle.kts and libs.versions.toml.
Set up docker-compose for local development (Zeebe, MongoDB, Elasticsearch, etc.).
Integrate CI pipeline for ./gradlew check.
Acceptance Criteria:  
Gradle modules follow dependency rules.
CI pipeline runs check successfully.
Local environment is functional with docker-compose.