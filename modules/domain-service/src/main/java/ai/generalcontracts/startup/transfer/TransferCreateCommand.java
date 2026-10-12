package ai.generalcontracts.startup.transfer;

public record TransferCreateCommand(
    String sourceWallet,
    String destinationWallet,
    String tokenIdentifier,
    String amount,
    long networkId,
    String idempotencyKey,
    String tenantId,
    String initiatingPrincipal
) {
}
