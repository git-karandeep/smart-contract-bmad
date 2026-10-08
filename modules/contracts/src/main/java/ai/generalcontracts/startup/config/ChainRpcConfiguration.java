package ai.generalcontracts.startup.config;

import ai.generalcontracts.chain.ChainRpcClient;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class ChainRpcConfiguration {
    @Bean(destroyMethod = "shutdown")
    Web3j web3j(
        @Value("${chain.rpc-url}") String rpcUrl,
        @Value("${chain.rpc-timeout-seconds:5}") int timeoutSeconds
    ) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("CHAIN_RPC_TIMEOUT_SECONDS must be greater than zero");
        }
        var httpClient = HttpService.getOkHttpClientBuilder()
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build();
        return Web3j.build(new HttpService(rpcUrl, httpClient, false));
    }

    @Bean
    ChainRpcClient chainRpcClient(Web3j web3j, @Value("${chain.chain-id}") BigInteger expectedChainId) {
        return new ChainRpcClient(web3j, expectedChainId);
    }
}
