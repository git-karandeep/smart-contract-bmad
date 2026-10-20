package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.chain.TransferSubmission;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import java.util.Optional;

public interface TransferSubmissionPort {
    TransferSubmission submit(TransferInstruction instruction);

    default Optional<TransferSubmission> findJournaledSubmission(String instructionId) {
        return Optional.empty();
    }
}
