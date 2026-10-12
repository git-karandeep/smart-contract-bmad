package ai.generalcontracts.chain.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class TransactionFinalityPolicyTest {
    private final TransactionFinalityPolicy policy = new TransactionFinalityPolicy(
        BigInteger.valueOf(1337), BigInteger.valueOf(3)
    );

    @Test
    void waitsForConfiguredConfirmationDepth() {
        TransactionObservation observation = observation(BigInteger.valueOf(10), true, false);
        assertEquals(TransactionOutcome.PENDING, policy.evaluate(observation, BigInteger.valueOf(11)));
        assertEquals(TransactionOutcome.CONFIRMED, policy.evaluate(observation, BigInteger.valueOf(12)));
    }

    @Test
    void neverConfirmsRevertedReplacedOrUnknownTransactions() {
        assertEquals(TransactionOutcome.PENDING, policy.evaluate(
            observation(BigInteger.ONE, false, false), BigInteger.ONE
        ));
        assertEquals(TransactionOutcome.REVERTED, policy.evaluate(
            observation(BigInteger.ONE, false, false), BigInteger.valueOf(3)
        ));
        assertEquals(TransactionOutcome.REPLACED, policy.evaluate(
            observation(BigInteger.TEN, true, true), BigInteger.TEN
        ));
        assertEquals(TransactionOutcome.UNKNOWN, policy.evaluate(
            new TransactionObservation(BigInteger.TEN, "0xhash", null, true, false), BigInteger.TEN
        ));
    }

    private TransactionObservation observation(BigInteger block, boolean success, boolean replaced) {
        return new TransactionObservation(BigInteger.valueOf(1337), "0xhash", block, success, replaced);
    }
}
