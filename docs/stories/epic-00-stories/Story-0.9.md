Story 0.9 — Create Solidity Project

As a smart-contract developer,
I want an independently buildable Solidity project,
so that contracts can be developed and tested separately from the Java application.

Structure
smart-contracts/

├── src/
│   ├── Token.sol
│   ├── Compliance.sol
│   └── Settlement.sol
│
├── test/
├── script/
├── interfaces/
└── README.md
Acceptance Criteria
Solidity compiler configured
Contract compilation succeeds
Contract test framework configured
Sample contract deploys locally