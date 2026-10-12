package ai.generalcontracts.chain;

public record TransferSubmission(
    String instructionId,
    long networkId,
    String tokenContract,
    String source,
    String destination,
    String amount,
    String transactionHash,
    Outcome outcome,
    String reasonCode
) {
    public enum Outcome {
        SUBMITTED,
        REJECTED,
        UNKNOWN
    }
}
