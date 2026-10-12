package ai.generalcontracts.chain.listener;

import java.math.BigInteger;

public record TransactionObservation(
    BigInteger networkId,
    String transactionHash,
    BigInteger includedBlock,
    boolean receiptSuccess,
    boolean replaced
) {
}
