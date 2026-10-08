Story 0.5 — Configure Application Profiles

As a developer,
I want separate application configurations for environments,
so that development, test, and production settings are isolated.

Profiles
application.yml
application-local.yml
application-test.yml
application-prod.yml
Acceptance Criteria
Local profile works
Test profile works
Production configuration contains no secrets
Environment variables override defaults
Sensitive configuration is externalized