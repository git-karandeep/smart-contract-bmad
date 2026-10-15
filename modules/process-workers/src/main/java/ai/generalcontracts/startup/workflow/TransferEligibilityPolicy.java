package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.startup.transfer.TransferInstruction;

@FunctionalInterface
public interface TransferEligibilityPolicy {
    TransferValidationPolicy.ValidationDecision validate(TransferInstruction instruction);
}
