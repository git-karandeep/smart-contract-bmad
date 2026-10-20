package ai.generalcontracts.chain.listener;

import java.math.BigInteger;

public record TransactionStatusReport(
    String instructionId,
    BigInteger networkId,
    String transactionHash,
    TransactionOutcome outcome,
    boolean finalized,
    String reasonCode
) {
}
