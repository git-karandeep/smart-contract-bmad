package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.chain.TransferSubmission;
import ai.generalcontracts.startup.transfer.TransferInstruction;

public interface TransferSubmissionPort {
    TransferSubmission submit(TransferInstruction instruction);
}
