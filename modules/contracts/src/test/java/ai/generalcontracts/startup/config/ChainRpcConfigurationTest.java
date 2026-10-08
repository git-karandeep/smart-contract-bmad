package ai.generalcontracts.startup.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChainRpcConfigurationTest {
    @Test
    void rejectsNonPositiveRpcTimeouts() {
        assertThatThrownBy(() -> new ChainRpcConfiguration().web3j("http://127.0.0.1:8545", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("CHAIN_RPC_TIMEOUT_SECONDS must be greater than zero");

        assertThatThrownBy(() -> new ChainRpcConfiguration().web3j("http://127.0.0.1:8545", -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("CHAIN_RPC_TIMEOUT_SECONDS must be greater than zero");
    }
}
