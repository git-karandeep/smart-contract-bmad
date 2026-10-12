package ai.generalcontracts.chain.listener;

import java.math.BigInteger;

public record TransferEventExpectation(
    BigInteger networkId,
    String transactionHash,
    String contractAddress,
    String source,
    String destination,
    BigInteger amountInBaseUnits
) {
}
