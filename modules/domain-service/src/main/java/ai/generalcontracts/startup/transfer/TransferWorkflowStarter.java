package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.startup.transfer.TransferInstruction;

public interface TransferWorkflowStarter {
    void start(TransferInstruction instruction);
}
