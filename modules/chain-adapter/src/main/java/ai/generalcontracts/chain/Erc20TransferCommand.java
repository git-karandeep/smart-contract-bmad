package ai.generalcontracts.chain;

public record Erc20TransferCommand(
    String instructionId,
    long networkId,
    String tokenContract,
    String source,
    String destination,
    String decimalAmount
) {
}
