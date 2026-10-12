package ai.generalcontracts.startup.workflow;

import java.time.Instant;

public record ScreeningRequestMessage(
    String instructionId,
    String screeningRequestId,
    String correlationId,
    String providerId,
    ScreeningType screeningType,
    String screeningVersion,
    Instant requestedAt
) {
}
