package ai.generalcontracts.startup.transfer;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "screening_results")
@CompoundIndexes({
    @CompoundIndex(name = "provider_message_unique", def = "{'providerId': 1, 'messageId': 1}", unique = true),
    @CompoundIndex(name = "screening_request_result_unique", def = "{'requestId': 1}", unique = true)
})
public class ScreeningResultRecord {
    @Id
    private String id;
    private String providerId;
    private String messageId;
    private String instructionId;
    private String requestId;
    private String correlationId;
    private String screeningType;
    private String decision;
    private Instant screenedAt;
    private Instant receivedAt;
    private Instant correlatedAt;
    private String correlationDisposition;

    public ScreeningResultRecord() {
    }

    public ScreeningResultRecord(
        String id,
        String providerId,
        String messageId,
        String instructionId,
        String requestId,
        String correlationId,
        String screeningType,
        String decision,
        Instant screenedAt,
        Instant receivedAt
    ) {
        this.id = id;
        this.providerId = providerId;
        this.messageId = messageId;
        this.instructionId = instructionId;
        this.requestId = requestId;
        this.correlationId = correlationId;
        this.screeningType = screeningType;
        this.decision = decision;
        this.screenedAt = screenedAt;
        this.receivedAt = receivedAt;
    }

    public String getId() { return id; }
    public String getProviderId() { return providerId; }
    public String getMessageId() { return messageId; }
    public String getInstructionId() { return instructionId; }
    public String getRequestId() { return requestId; }
    public String getCorrelationId() { return correlationId; }
    public String getScreeningType() { return screeningType; }
    public String getDecision() { return decision; }
    public Instant getScreenedAt() { return screenedAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getCorrelatedAt() { return correlatedAt; }
    public String getCorrelationDisposition() { return correlationDisposition; }
    public void setCorrelatedAt(Instant correlatedAt) { this.correlatedAt = correlatedAt; }
    public void setCorrelationDisposition(String correlationDisposition) {
        this.correlationDisposition = correlationDisposition;
    }
}
