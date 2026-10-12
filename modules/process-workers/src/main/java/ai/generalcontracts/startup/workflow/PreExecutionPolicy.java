package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.startup.transfer.TransferInstruction;

public interface PreExecutionPolicy {
    boolean isEligible(TransferInstruction instruction);
}
