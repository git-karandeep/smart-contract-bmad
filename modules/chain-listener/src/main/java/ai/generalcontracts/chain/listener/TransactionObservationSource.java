package ai.generalcontracts.chain.listener;

import java.math.BigInteger;
import java.util.Optional;

public interface TransactionObservationSource {
    /** Returns the receipt-based observation, or empty while the transaction is unknown to the node. */
    Optional<TransactionObservation> observe(BigInteger networkId, String transactionHash);

    BigInteger latestBlock(BigInteger networkId);
}
