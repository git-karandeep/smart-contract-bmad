sequenceDiagram
participant Client
participant API as api-gateway
participant Zeebe as Camunda 8
participant Workers as process-workers
participant Domain as domain-service
participant Mongo as MongoDB
participant Chain as chain-adapter
participant BC as Blockchain
participant Listener as chain-listener

    Client->>API: POST /transfers
    API->>Mongo: Save Transfer(PENDING) + Outbox
    API->>Zeebe: Start Process Instance
    API-->>Client: 202 Accepted {businessId}
    
    Zeebe->>Workers: Job: validate-transfer
    Workers->>Domain: Call Use Case
    Domain->>Mongo: Load & Validate
    Domain-->>Workers: Result
    Workers->>Zeebe: Complete Job
    
    Zeebe->>Workers: Job: submit-escrow-tx
    Workers->>Domain: Call Use Case
    Domain->>Chain: submitTransaction()
    Chain->>BC: Sign & Send Transaction
    BC-->>Chain: txHash
    Chain-->>Domain: txHash
    Domain->>Mongo: Update Transfer(SUBMITTED)
    Domain->>Mongo: Write Outbox Event
    Domain-->>Workers: Result
    Workers->>Zeebe: Complete Job
    
    Note over Zeebe: Wait for Message Event<br/>TxConfirmed
    
    BC-->>Listener: Transaction Confirmed
    Listener->>Listener: Wait N Confirmations
    Listener->>Zeebe: Publish Message TxConfirmed
    Listener->>Mongo: Store Raw Event
    
    Zeebe->>Workers: Job: finalize-transfer
    Workers->>Domain: Call Use Case
    Domain->>Mongo: Update Transfer(SETTLED)
    Domain->>Mongo: Write Outbox Event
    Domain-->>Workers: Result
    Workers->>Zeebe: Complete Job
    
    Zeebe-->>Client: Process Complete


graph TB
subgraph "External Clients"
Client[Web Client / REST API]
end

    subgraph "Spring Boot Application Layer"
        APIGateway[api-gateway<br/>REST Controllers<br/>AuthN/Z]
        AppBootstrap[app-bootstrap<br/>Spring Boot Main<br/>Configuration]
    end

    subgraph "Domain Layer"
        DomainService[domain-service<br/>Use Cases<br/>Business Logic]
        CommonDomain[common-domain<br/>Value Objects<br/>Domain Events<br/>Ports]
    end

    subgraph "Orchestration Layer"
        ProcessWorkers[process-workers<br/>@JobWorker Handlers<br/>Thin Layer]
        ProcessDefinitions[process-definitions<br/>BPMN/DMN Files<br/>Process Flows]
    end

    subgraph "Integration Adapters"
        ChainAdapter[chain-adapter<br/>Web3j Implementation<br/>ChainPort]
        ChainListener[chain-listener<br/>Event Subscription<br/>Confirmation Handling]
        PersistenceMongo[persistence-mongo<br/>MongoDB Repositories<br/>Outbox Pattern]
    end

    subgraph "Infrastructure"
        Zeebe[(Camunda 8<br/>Zeebe + Elasticsearch)]
        Mongo[(MongoDB<br/>Domain Data<br/>Projections)]
        Blockchain[(Blockchain Node<br/>+ Smart Contracts)]
        Kafka[Kafka<br/>Event Streaming]
    end

    subgraph "Current Implementation"
        ContractsModule[contracts module<br/>Basic Order Management<br/>MongoDB + Spring Boot]
    end

    Client --> APIGateway
    APIGateway --> DomainService
    APIGateway -->|Start Process| Zeebe
    
    Zeebe <-->|Jobs| ProcessWorkers
    ProcessWorkers --> DomainService
    ProcessWorkers --> ProcessDefinitions
    
    DomainService --> CommonDomain
    DomainService -.Port Interface.-> ChainAdapter
    DomainService -.Port Interface.-> PersistenceMongo
    
    ChainAdapter --> Blockchain
    ChainListener --> Blockchain
    ChainListener -->|Publish Message| Zeebe
    ChainListener --> Mongo
    
    PersistenceMongo --> Mongo
    DomainService --> Mongo
    
    ProcessWorkers --> Mongo
    
    ContractsModule -.Early Prototype.-> Mongo
    ContractsModule -.Not Connected.-> Zeebe
    ContractsModule -.Not Connected.-> Blockchain

    style ContractsModule fill:#f9f,stroke:#333,stroke-dasharray: 5 5
    style CommonDomain fill:#bbf,stroke:#333
    style DomainService fill:#bfb,stroke:#333