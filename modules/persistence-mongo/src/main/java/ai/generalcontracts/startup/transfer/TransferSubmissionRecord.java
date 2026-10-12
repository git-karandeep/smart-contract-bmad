package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.chain.SubmissionIntent;
import ai.generalcontracts.chain.TransferSubmission;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "transfer_submissions")
public class TransferSubmissionRecord {
    @Id
    private String instructionId;
    private long networkId;
    private String tokenContract;
    private String source;
    private String destination;
    private String amount;
    private String status;
    private String reasonCode;
    private String transactionHash;

    public TransferSubmissionRecord() {
    }

    public TransferSubmissionRecord(SubmissionIntent intent) {
        this.instructionId = intent.instructionId();
        this.networkId = intent.networkId();
        this.tokenContract = intent.tokenContract();
        this.source = intent.source();
        this.destination = intent.destination();
        this.amount = intent.decimalAmount();
        this.status = "SUBMISSION_IN_PROGRESS";
    }

    public String getInstructionId() { return instructionId; }
    public long getNetworkId() { return networkId; }
    public String getTokenContract() { return tokenContract; }
    public String getSource() { return source; }
    public String getDestination() { return destination; }
    public String getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getReasonCode() { return reasonCode; }
    public String getTransactionHash() { return transactionHash; }

    public TransferSubmission toSubmission() {
        TransferSubmission.Outcome outcome;
        try {
            outcome = TransferSubmission.Outcome.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException missingOutcome) {
            outcome = TransferSubmission.Outcome.UNKNOWN;
        }
        return new TransferSubmission(
            instructionId,
            networkId,
            tokenContract,
            source,
            destination,
            amount,
            transactionHash,
            outcome,
            reasonCode
        );
    }

    public void apply(TransferSubmission submission) {
        this.status = submission.outcome().name();
        this.reasonCode = submission.reasonCode();
        this.transactionHash = submission.transactionHash();
    }
}
