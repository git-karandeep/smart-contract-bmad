package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.startup.workflow.ScreeningResult;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.Map;

public class CamundaScreeningWorkflowCorrelator implements ScreeningWorkflowCorrelator {
    private final ZeebeClient client;

    public CamundaScreeningWorkflowCorrelator(ZeebeClient client) {
        this.client = client;
    }

    @Override
    public void correlate(ScreeningResult result) {
        client.newPublishMessageCommand()
            .messageName(messageName(result))
            .correlationKey(result.correlationId())
            .messageId(result.providerId() + ":" + result.messageId())
            .variables(Map.of(
                "instructionId", result.instructionId(),
                "screeningRequestId", result.screeningRequestId(),
                "screeningType", result.screeningType().name(),
                "screeningDecision", result.decision().name(),
                "screenedAt", result.screenedAt().toString()
            ))
            .send()
            .join();
    }

    private String messageName(ScreeningResult result) {
        return switch (result.screeningType()) {
            case KYC -> "KycScreeningResult";
            case AML -> "AmlScreeningResult";
            case SANCTIONS -> "SanctionsScreeningResult";
        };
    }
}
