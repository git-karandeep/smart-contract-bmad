package ai.generalcontracts.startup.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScreeningRequestMessage(
    String instructionId,
    String screeningRequestId,
    String correlationId,
    String providerId,
    ScreeningType screeningType,
    String screeningVersion,
    Instant requestedAt,
    String subjectReference,
    Map<String, String> authorizedReferences,
    String policyVersion
) {
    public ScreeningRequestMessage {
        authorizedReferences = authorizedReferences == null ? null : Map.copyOf(authorizedReferences);
    }

    public ScreeningRequestMessage(
        String instructionId,
        String screeningRequestId,
        String correlationId,
        String providerId,
        ScreeningType screeningType,
        String screeningVersion,
        Instant requestedAt
    ) {
        this(
            instructionId,
            screeningRequestId,
            correlationId,
            providerId,
            screeningType,
            screeningVersion,
            requestedAt,
            null,
            null,
            null
        );
    }
}
