package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.startup.workflow.ScreeningResult;
import ai.generalcontracts.startup.workflow.ScreeningType;
import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import java.util.Map;

public class CamundaScreeningWorkflowCorrelator implements ScreeningWorkflowCorrelator {
    private final ZeebeClient client;

    public CamundaScreeningWorkflowCorrelator(ZeebeClient client) {
        this.client = client;
    }

    @Override
    public void correlate(ScreeningResult result) {
        var command = client.newPublishMessageCommand()
            .messageName(messageName(result))
            .correlationKey(result.screeningType() == ScreeningType.KYC
                ? result.screeningRequestId()
                : result.instructionId() + ":" + result.screeningType().name())
            .messageId(result.providerId() + ":" + result.messageId());
        if (result.screeningType() == ScreeningType.KYC) {
            command = command.timeToLive(Duration.ofMinutes(15));
        }
        command.variables(Map.of()).send().join();
    }

    private String messageName(ScreeningResult result) {
        return switch (result.screeningType()) {
            case KYC -> "KycScreeningResult";
            case AML -> "AmlScreeningResult";
            case SANCTIONS -> "SanctionsScreeningResult";
        };
    }
}
