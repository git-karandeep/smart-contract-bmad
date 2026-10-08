package ai.generalcontracts.chain;

import java.io.IOException;
import java.math.BigInteger;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthChainId;

public final class ChainRpcClient {
    private final Web3j web3j;
    private final BigInteger expectedChainId;

    public ChainRpcClient(Web3j web3j) {
        this(web3j, null);
    }

    public ChainRpcClient(Web3j web3j, BigInteger expectedChainId) {
        this.web3j = web3j;
        this.expectedChainId = expectedChainId;
    }

    public ChainStatus status() {
        try {
            EthChainId chainId = web3j.ethChainId().send();
            EthBlockNumber latestBlock = web3j.ethBlockNumber().send();
            if (chainId.hasError() || latestBlock.hasError()) {
                throw new ChainConnectionException("The configured EVM RPC endpoint returned an error", null);
            }
            BigInteger actualChainId = chainId.getChainId();
            if (expectedChainId != null && !expectedChainId.equals(actualChainId)) {
                throw new ChainConnectionException("The configured EVM RPC endpoint returned an unexpected chain ID", null);
            }
            return new ChainStatus(actualChainId, latestBlock.getBlockNumber());
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ChainConnectionException chainException) {
                throw chainException;
            }
            throw new ChainConnectionException(
                "Unable to query the configured EVM RPC endpoint (" + exception.getClass().getSimpleName() + ")",
                null
            );
        }
    }
}
