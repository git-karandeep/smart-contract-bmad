package ai.generalcontracts.chain;

import java.util.Optional;

public interface SubmissionJournal {
    boolean claim(SubmissionIntent intent);

    void record(TransferSubmission submission);

    Optional<TransferSubmission> find(String instructionId);
}
