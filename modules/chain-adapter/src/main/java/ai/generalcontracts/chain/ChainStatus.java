package ai.generalcontracts.chain;

import java.math.BigInteger;

public record ChainStatus(BigInteger chainId, BigInteger latestBlock) {
}
