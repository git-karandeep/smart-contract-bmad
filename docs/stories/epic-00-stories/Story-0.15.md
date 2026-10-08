Story 0.15 — Establish CI Pipeline

As a development team,
I want every code change automatically validated,
so that broken builds and security issues are detected early.

Pipeline
Commit
│
▼
Build
│
▼
Unit Tests
│
▼
Integration Tests
│
▼
Smart Contract Tests
│
▼
Security Scan
│
▼
Container Build
Acceptance Criteria
CI executes automatically
Build failure blocks merge
Tests execute
Security scanning executes
Build artifacts are generated