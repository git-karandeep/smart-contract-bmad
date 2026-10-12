package ai.generalcontracts.startup.workflow;

import java.time.Instant;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

public record ScreeningResult(
    String providerId,
    String messageId,
    String instructionId,
    String screeningRequestId,
    String correlationId,
    ScreeningType screeningType,
    ScreeningDecision decision,
    Instant screenedAt
) {
    public String canonicalMessage() {
        if (screeningType == null || decision == null || screenedAt == null) {
            throw new IllegalArgumentException("Screening result is incomplete");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                for (String field : new String[] {
                    value(providerId), value(messageId), value(instructionId), value(screeningRequestId),
                    value(correlationId), screeningType.name(), decision.name(), screenedAt.toString()
                }) {
                    byte[] encoded = field.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    output.writeInt(encoded.length);
                    output.write(encoded);
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to encode screening result", impossible);
        }
    }

    private String value(String field) {
        return field == null ? "" : field;
    }
}
