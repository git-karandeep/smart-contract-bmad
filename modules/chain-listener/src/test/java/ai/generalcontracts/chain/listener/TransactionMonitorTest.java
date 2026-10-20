package ai.generalcontracts.chain.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TransactionMonitorTest {
    private static final BigInteger NETWORK = BigInteger.valueOf(1337);
    private static final String HASH = "0xabc";

    private final AtomicReference<Optional<TransactionObservation>> observed = new AtomicReference<>(Optional.empty());
    private final AtomicReference<BigInteger> latest = new AtomicReference<>(BigInteger.TEN);
    private boolean rpcDown;
    private final TransactionMonitor monitor = new TransactionMonitor(
        new TransactionObservationSource() {
            @Override
            public Optional<TransactionObservation> observe(BigInteger networkId, String hash) {
                if (rpcDown) {
                    throw new IllegalStateException("rpc down");
                }
                return observed.get();
            }

            @Override
            public BigInteger latestBlock(BigInteger networkId) {
                return latest.get();
            }
        },
        new TransactionFinalityPolicy(NETWORK, BigInteger.valueOf(3))
    );

    @Test
    void movesFromUnobservedToPendingToFinalWithoutConflatingStates() {
        assertEquals(TransactionOutcome.UNKNOWN, monitor.check("t-1", NETWORK, HASH).outcome());

        observed.set(Optional.of(new TransactionObservation(NETWORK, HASH, null, false, false)));
        TransactionStatusReport pending = monitor.check("t-1", NETWORK, HASH);
        assertEquals(TransactionOutcome.PENDING, pending.outcome());
        assertFalse(pending.finalized());

        observed.set(Optional.of(new TransactionObservation(NETWORK, HASH, BigInteger.TEN, true, false)));
        assertEquals(TransactionOutcome.PENDING, monitor.check("t-1", NETWORK, HASH).outcome());

        latest.set(BigInteger.valueOf(12));
        TransactionStatusReport confirmed = monitor.check("t-1", NETWORK, HASH);
        assertEquals(TransactionOutcome.CONFIRMED, confirmed.outcome());
        assertTrue(confirmed.finalized());
        assertEquals("t-1", confirmed.instructionId());
        assertEquals(HASH, confirmed.transactionHash());
    }

    @Test
    void finalRevertAndReplacementAreDistinct() {
        latest.set(BigInteger.valueOf(20));
        observed.set(Optional.of(new TransactionObservation(NETWORK, HASH, BigInteger.TEN, false, false)));
        TransactionStatusReport reverted = monitor.check("t-1", NETWORK, HASH);
        assertEquals(TransactionOutcome.REVERTED, reverted.outcome());
        assertTrue(reverted.finalized());

        observed.set(Optional.of(new TransactionObservation(NETWORK, HASH, BigInteger.TEN, true, true)));
        TransactionStatusReport replaced = monitor.check("t-1", NETWORK, HASH);
        assertEquals(TransactionOutcome.REPLACED, replaced.outcome());
        assertFalse(replaced.finalized());
    }

    @Test
    void rpcFailureMismatchAndMissingReferenceAreUnknownNeverFinal() {
        rpcDown = true;
        TransactionStatusReport failed = monitor.check("t-1", NETWORK, HASH);
        assertEquals(TransactionOutcome.UNKNOWN, failed.outcome());
        assertEquals("CHAIN_RPC_UNAVAILABLE", failed.reasonCode());
        assertFalse(failed.finalized());

        rpcDown = false;
        observed.set(Optional.of(new TransactionObservation(NETWORK, "0xother", BigInteger.ONE, true, false)));
        assertEquals("TRANSACTION_OBSERVATION_MISMATCH", monitor.check("t-1", NETWORK, HASH).reasonCode());
        assertEquals("TRANSACTION_REFERENCE_MISSING", monitor.check("t-1", NETWORK, " ").reasonCode());
    }
}
