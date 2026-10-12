package ai.generalcontracts.chain.listener;

import java.math.BigInteger;

public record ObservedTransferEvent(
    BigInteger networkId,
    String transactionHash,
    String contractAddress,
    String source,
    String destination,
    BigInteger amountInBaseUnits
) {
}
