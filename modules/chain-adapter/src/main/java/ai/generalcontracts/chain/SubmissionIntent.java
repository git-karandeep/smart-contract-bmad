package ai.generalcontracts.chain;

public record SubmissionIntent(
    String instructionId,
    long networkId,
    String tokenContract,
    String source,
    String destination,
    String decimalAmount
) {
}
