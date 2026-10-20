package ai.generalcontracts.chain.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class TransferReconciliationTest {
    private final TransferReconciliation reconciler = new TransferReconciliation();

    @Test
    void matchesExpectedEventIgnoringAddressLetterCase() {
        TransferReconciliation.Result result = reconciler.reconcile(expected(), observed());
        assertTrue(result.matched());
    }

    @Test
    void holdsForMissingEventOrMismatch() {
        assertEquals("TRANSFER_EVENT_MISSING", reconciler.reconcile(expected(), null).reasonCode());
        ObservedTransferEvent mismatched = new ObservedTransferEvent(
            BigInteger.valueOf(1337), "0xhash", "0x3333333333333333333333333333333333333333",
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222", BigInteger.valueOf(11)
        );
        assertEquals("AMOUNT_MISMATCH", reconciler.reconcile(expected(), mismatched).reasonCode());
    }

    @Test
    void reportsAStableReasonForEachMaterialFieldMismatch() {
        String token = "0x3333333333333333333333333333333333333333";
        String source = "0x1111111111111111111111111111111111111111";
        String destination = "0x2222222222222222222222222222222222222222";
        String other = "0x4444444444444444444444444444444444444444";
        assertEquals("NETWORK_MISMATCH", reconciler.reconcile(expected(), new ObservedTransferEvent(
            BigInteger.ONE, "0xhash", token, source, destination, BigInteger.TEN)).reasonCode());
        assertEquals("TRANSACTION_HASH_MISMATCH", reconciler.reconcile(expected(), new ObservedTransferEvent(
            BigInteger.valueOf(1337), "0xother", token, source, destination, BigInteger.TEN)).reasonCode());
        assertEquals("TOKEN_CONTRACT_MISMATCH", reconciler.reconcile(expected(), new ObservedTransferEvent(
            BigInteger.valueOf(1337), "0xhash", other, source, destination, BigInteger.TEN)).reasonCode());
        assertEquals("WALLET_MISMATCH", reconciler.reconcile(expected(), new ObservedTransferEvent(
            BigInteger.valueOf(1337), "0xhash", token, other, destination, BigInteger.TEN)).reasonCode());
        assertEquals("WALLET_MISMATCH", reconciler.reconcile(expected(), new ObservedTransferEvent(
            BigInteger.valueOf(1337), "0xhash", token, source, other, BigInteger.TEN)).reasonCode());
    }

    private TransferEventExpectation expected() {
        return new TransferEventExpectation(
            BigInteger.valueOf(1337), "0xhash", "0x3333333333333333333333333333333333333333",
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222", BigInteger.valueOf(10)
        );
    }

    private ObservedTransferEvent observed() {
        return new ObservedTransferEvent(
            BigInteger.valueOf(1337), "0xHASH", "0x3333333333333333333333333333333333333333",
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222", BigInteger.valueOf(10)
        );
    }
}
