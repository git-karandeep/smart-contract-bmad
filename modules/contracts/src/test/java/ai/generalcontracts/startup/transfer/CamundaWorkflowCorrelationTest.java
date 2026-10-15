package ai.generalcontracts.startup.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ai.generalcontracts.startup.workflow.ScreeningDecision;
import ai.generalcontracts.startup.workflow.ScreeningResult;
import ai.generalcontracts.startup.workflow.ScreeningType;
import io.camunda.zeebe.client.ZeebeClient;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CamundaWorkflowCorrelationTest {
    @Test
    void startsByInstructionIdWithOnlyInstructionAndScreeningCorrelationKeys() {
        ZeebeClient client = mock(ZeebeClient.class, RETURNS_DEEP_STUBS);
        TransferInstruction instruction = new TransferInstruction();
        instruction.setInstructionId("transfer-1");
        instruction.setCorrelationId("unrelated-correlation");
        instruction.setSourceWallet("wallet-secret");
        instruction.setDestinationWallet("destination");
        instruction.setTokenIdentifier("token");
        instruction.setAmount("10");
        instruction.setNetworkId(1337);

        new CamundaTransferWorkflowStarter(client).start(instruction);

        ArgumentCaptor<Map<String, Object>> variables = mapCaptor();
        verify(client.newPublishMessageCommand()
            .messageName("TransferInstructionReceived")
            .correlationKey("transfer-1")
            .messageId("transfer-instruction:transfer-1")
        ).variables(variables.capture());
        assertEquals(Map.of(
            "instructionId", "transfer-1",
            "kycCorrelationKey", "transfer-1:KYC",
            "amlCorrelationKey", "transfer-1:AML",
            "sanctionsCorrelationKey", "transfer-1:SANCTIONS"
        ), variables.getValue());
    }

    @Test
    void correlatesScreeningResultsUsingInstructionAndTypeWithoutAddingWorkflowData() {
        ZeebeClient client = mock(ZeebeClient.class, RETURNS_DEEP_STUBS);
        ScreeningResult result = new ScreeningResult(
            "provider-1",
            "message-1",
            "transfer-1",
            "transfer-1:AML",
            "mismatched-caller-key",
            ScreeningType.AML,
            ScreeningDecision.APPROVED,
            Instant.parse("2026-10-15T00:00:00Z")
        );

        new CamundaScreeningWorkflowCorrelator(client).correlate(result);

        ArgumentCaptor<Map<String, Object>> variables = mapCaptor();
        verify(client.newPublishMessageCommand()
            .messageName("AmlScreeningResult")
            .correlationKey("transfer-1:AML")
            .messageId("provider-1:message-1")
        ).variables(variables.capture());
        assertEquals(Map.of(), variables.getValue());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
    }
}
