package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.startup.transfer.TransferInstruction;

public interface TransferValidationPolicy {
    ValidationDecision validate(TransferInstruction instruction);

    record ValidationDecision(boolean valid, String reasonCode) {
        public static ValidationDecision rejected(String reasonCode) {
            return new ValidationDecision(false, reasonCode);
        }
    }
}
