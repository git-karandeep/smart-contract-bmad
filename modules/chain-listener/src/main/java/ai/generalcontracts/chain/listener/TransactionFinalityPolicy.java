package ai.generalcontracts.chain.listener;

import java.math.BigInteger;

public final class TransactionFinalityPolicy {
    private final BigInteger configuredNetworkId;
    private final BigInteger requiredConfirmations;

    public TransactionFinalityPolicy(BigInteger configuredNetworkId, BigInteger requiredConfirmations) {
        if (configuredNetworkId == null || configuredNetworkId.signum() <= 0
            || requiredConfirmations == null || requiredConfirmations.signum() <= 0) {
            throw new IllegalArgumentException("Network and finality configuration must be positive");
        }
        this.configuredNetworkId = configuredNetworkId;
        this.requiredConfirmations = requiredConfirmations;
    }

    public TransactionOutcome evaluate(TransactionObservation observation, BigInteger latestBlock) {
        if (observation == null || latestBlock == null || observation.transactionHash() == null
            || observation.transactionHash().isBlank()
            || !configuredNetworkId.equals(observation.networkId())) {
            return TransactionOutcome.UNKNOWN;
        }
        if (observation.replaced()) {
            return TransactionOutcome.REPLACED;
        }
        if (observation.includedBlock() == null) {
            return TransactionOutcome.PENDING;
        }
        if (latestBlock.compareTo(observation.includedBlock()) < 0) {
            return TransactionOutcome.UNKNOWN;
        }
        BigInteger confirmations = latestBlock.subtract(observation.includedBlock()).add(BigInteger.ONE);
        if (confirmations.compareTo(requiredConfirmations) < 0) {
            return TransactionOutcome.PENDING;
        }
        return observation.receiptSuccess() ? TransactionOutcome.CONFIRMED : TransactionOutcome.REVERTED;
    }
}
