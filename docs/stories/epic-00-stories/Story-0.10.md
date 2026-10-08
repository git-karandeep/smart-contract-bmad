Story 0.10 — Configure Web3j Integration

As a backend developer,
I want Spring Boot to communicate with the EVM network through Web3j,
so that Java services can interact with smart contracts.

Flow
Spring Boot
│
▼
Web3j
│
▼
JSON-RPC
│
▼
EVM Node
Acceptance Criteria
Web3j dependency configured
RPC connection established
Chain ID retrieved
Latest block retrieved
Connection failure is handled
Web3 configuration is externalized