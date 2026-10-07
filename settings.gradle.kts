rootProject.name = "smart-contracts-startup"

include("modules:contracts")
findProject(":modules:contracts")?.name = "contracts"

include("modules:common-domain")
findProject(":modules:common-domain")?.name = "common-domain"

include("modules:common-observability")
findProject(":modules:common-observability")?.name = "common-observability"

include("modules:chain-adapter")
findProject(":modules:chain-adapter")?.name = "chain-adapter"

include("modules:chain-listener")
findProject(":modules:chain-listener")?.name = "chain-listener"

include("modules:persistence-mongo")
findProject(":modules:persistence-mongo")?.name = "persistence-mongo"

include("modules:process-definitions")
findProject(":modules:process-definitions")?.name = "process-definitions"

include("modules:process-workers")
findProject(":modules:process-workers")?.name = "process-workers"

include("modules:domain-service")
findProject(":modules:domain-service")?.name = "domain-service"

include("modules:api-gateway")
findProject(":modules:api-gateway")?.name = "api-gateway"

include("modules:app-bootstrap")
findProject(":modules:app-bootstrap")?.name = "app-bootstrap"

include("modules:solidity-contract")
findProject(":modules:solidity-contract")?.name = "solidity-contract"
