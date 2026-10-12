package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.startup.transfer.TransferInstruction;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.Map;

public final class CamundaTransferWorkflowStarter implements TransferWorkflowStarter {
    private final ZeebeClient client;

    public CamundaTransferWorkflowStarter(ZeebeClient client) {
        this.client = client;
    }

    @Override
    public void start(TransferInstruction instruction) {
        client.newPublishMessageCommand()
            .messageName("TransferInstructionReceived")
            .correlationKey(instruction.getInstructionId())
            .messageId("transfer-instruction:" + instruction.getInstructionId())
            .variables(Map.of(
                "instructionId", instruction.getInstructionId(),
                "correlationId", instruction.getCorrelationId(),
                "sourceWallet", instruction.getSourceWallet(),
                "destinationWallet", instruction.getDestinationWallet(),
                "tokenIdentifier", instruction.getTokenIdentifier(),
                "amount", instruction.getAmount(),
                "networkId", instruction.getNetworkId(),
                "kycCorrelationKey", instruction.getInstructionId() + ":KYC",
                "amlCorrelationKey", instruction.getInstructionId() + ":AML",
                "sanctionsCorrelationKey", instruction.getInstructionId() + ":SANCTIONS"
            ))
            .send()
            .join();
    }
}
