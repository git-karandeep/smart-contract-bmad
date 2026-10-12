package ai.generalcontracts.chain.listener;

import java.util.Locale;

public final class TransferReconciliation {
    public Result reconcile(TransferEventExpectation expected, ObservedTransferEvent observed) {
        if (expected == null || observed == null) {
            return new Result(false, "TRANSFER_EVENT_MISSING");
        }
        if (!expected.networkId().equals(observed.networkId())) {
            return new Result(false, "NETWORK_MISMATCH");
        }
        if (!same(expected.transactionHash(), observed.transactionHash())) {
            return new Result(false, "TRANSACTION_HASH_MISMATCH");
        }
        if (!same(expected.contractAddress(), observed.contractAddress())) {
            return new Result(false, "TOKEN_CONTRACT_MISMATCH");
        }
        if (!same(expected.source(), observed.source())
            || !same(expected.destination(), observed.destination())) {
            return new Result(false, "WALLET_MISMATCH");
        }
        if (!expected.amountInBaseUnits().equals(observed.amountInBaseUnits())) {
            return new Result(false, "AMOUNT_MISMATCH");
        }
        return new Result(true, null);
    }

    private boolean same(String left, String right) {
        return left != null && right != null
            && left.toLowerCase(Locale.ROOT).equals(right.toLowerCase(Locale.ROOT));
    }

    public record Result(boolean matched, String reasonCode) {
    }
}
