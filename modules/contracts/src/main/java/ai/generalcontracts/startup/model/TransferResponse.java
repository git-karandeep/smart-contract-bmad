package ai.generalcontracts.startup.model;

import ai.generalcontracts.startup.transfer.TransferInstruction;
import ai.generalcontracts.startup.transfer.TransferStatus;
import java.time.Instant;

public record TransferResponse(
    String instructionId,
    String sourceWallet,
    String destinationWallet,
    String tokenIdentifier,
    String amount,
    long networkId,
    TransferStatus status,
    String reasonCode,
    String transactionHash,
    Instant createdAt,
    Instant updatedAt
) {
    public static TransferResponse from(TransferInstruction instruction) {
        return new TransferResponse(
            instruction.getInstructionId(),
            instruction.getSourceWallet(),
            instruction.getDestinationWallet(),
            instruction.getTokenIdentifier(),
            instruction.getAmount(),
            instruction.getNetworkId(),
            instruction.getStatus(),
            instruction.getReasonCode(),
            instruction.getTransactionHash(),
            instruction.getCreatedAt(),
            instruction.getUpdatedAt()
        );
    }
}
