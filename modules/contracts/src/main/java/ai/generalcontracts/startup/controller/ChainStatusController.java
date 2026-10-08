package ai.generalcontracts.startup.controller;

import ai.generalcontracts.chain.ChainRpcClient;
import ai.generalcontracts.chain.ChainStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chain")
public class ChainStatusController {
    private final ChainRpcClient chainRpcClient;

    public ChainStatusController(ChainRpcClient chainRpcClient) {
        this.chainRpcClient = chainRpcClient;
    }

    @GetMapping("/status")
    public ChainStatus status() {
        return chainRpcClient.status();
    }
}
