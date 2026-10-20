package ai.generalcontracts.chain.listener;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Observes one already-submitted transaction. It never submits or resubmits: an unknown
 * or unobservable transaction is reported as UNKNOWN so the original hash is reconciled.
 */
public final class TransactionMonitor {
    private final TransactionObservationSource source;
    private final TransactionFinalityPolicy policy;

    public TransactionMonitor(TransactionObservationSource source, TransactionFinalityPolicy policy) {
        this.source = source;
        this.policy = policy;
    }

    public TransactionStatusReport check(String instructionId, BigInteger networkId, String transactionHash) {
        if (instructionId == null || instructionId.isBlank() || networkId == null
            || transactionHash == null || transactionHash.isBlank()) {
            return report(instructionId, networkId, transactionHash, TransactionOutcome.UNKNOWN, "TRANSACTION_REFERENCE_MISSING");
        }
        try {
            Optional<TransactionObservation> observed = source.observe(networkId, transactionHash);
            if (observed.isEmpty()) {
                return report(instructionId, networkId, transactionHash, TransactionOutcome.UNKNOWN, "TRANSACTION_NOT_OBSERVED");
            }
            TransactionObservation observation = observed.get();
            if (!transactionHash.equalsIgnoreCase(observation.transactionHash())
                || !networkId.equals(observation.networkId())) {
                return report(instructionId, networkId, transactionHash, TransactionOutcome.UNKNOWN, "TRANSACTION_OBSERVATION_MISMATCH");
            }
            TransactionOutcome outcome = policy.evaluate(observation, source.latestBlock(networkId));
            String reason = outcome == TransactionOutcome.UNKNOWN ? "CHAIN_OUTCOME_UNCERTAIN" : null;
            return report(instructionId, networkId, transactionHash, outcome, reason);
        } catch (RuntimeException rpcFailure) {
            return report(instructionId, networkId, transactionHash, TransactionOutcome.UNKNOWN, "CHAIN_RPC_UNAVAILABLE");
        }
    }

    private TransactionStatusReport report(
        String instructionId,
        BigInteger networkId,
        String hash,
        TransactionOutcome outcome,
        String reason
    ) {
        boolean finalized = outcome == TransactionOutcome.CONFIRMED || outcome == TransactionOutcome.REVERTED;
        return new TransactionStatusReport(instructionId, networkId, hash, outcome, finalized, reason);
    }
}
