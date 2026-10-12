package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.startup.transfer.TransferInstruction;

public interface TransferReconciliationPort {
    ReconciliationDecision reconcile(TransferInstruction instruction, String transactionHash);

    record ReconciliationDecision(boolean matched, String reasonCode) {
    }
}
