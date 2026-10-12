package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.chain.TokenAmountConverter;
import ai.generalcontracts.chain.listener.TransferEventExpectation;
import ai.generalcontracts.chain.listener.TransferReconciliation;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import java.math.BigInteger;
import org.springframework.stereotype.Component;

@Component
public class TransferEventReconciliationAdapter implements TransferReconciliationPort {
    private final AuthoritativeTransferEventSource events;
    private final TokenDecimalsPolicy decimals;
    private final TransferReconciliation reconciler = new TransferReconciliation();

    public TransferEventReconciliationAdapter(
        AuthoritativeTransferEventSource events,
        TokenDecimalsPolicy decimals
    ) {
        this.events = events;
        this.decimals = decimals;
    }

    @Override
    public ReconciliationDecision reconcile(TransferInstruction instruction, String transactionHash) {
        if (instruction == null || transactionHash == null || transactionHash.isBlank()) {
            return new ReconciliationDecision(false, "TRANSACTION_REFERENCE_MISSING");
        }
        var configuredDecimals = decimals.decimals(instruction.getNetworkId(), instruction.getTokenIdentifier());
        if (configuredDecimals.isEmpty()) {
            return new ReconciliationDecision(false, "TOKEN_DECIMALS_UNAVAILABLE");
        }
        BigInteger amount;
        try {
            amount = TokenAmountConverter.toBaseUnits(instruction.getAmount(), configuredDecimals.getAsInt());
        } catch (IllegalArgumentException invalidAmount) {
            return new ReconciliationDecision(false, "TRANSFER_AMOUNT_INVALID");
        }

        var expected = new TransferEventExpectation(
            BigInteger.valueOf(instruction.getNetworkId()),
            transactionHash,
            instruction.getTokenIdentifier(),
            instruction.getSourceWallet(),
            instruction.getDestinationWallet(),
            amount
        );
        var observed = events.findFinalizedTransfer(instruction.getInstructionId(), transactionHash).orElse(null);
        TransferReconciliation.Result result = reconciler.reconcile(expected, observed);
        return new ReconciliationDecision(result.matched(), result.reasonCode());
    }
}
