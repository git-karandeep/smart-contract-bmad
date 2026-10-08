Story 0.4 — Bootstrap Spring Boot Application

As a developer,
I want a running Spring Boot application,
so that subsequent services can be implemented on a stable application foundation.

Acceptance Criteria
Application starts successfully
/actuator/health returns UP
Application supports environment-specific configuration
Application shuts down gracefully
Basic logging is configured
Initial package structure

ai.generalcontracts.startup

├── api
├── config
├── config/security
├── application
├── domain
├── model
├── repository
├── service
├── worker
├── util
└── boot