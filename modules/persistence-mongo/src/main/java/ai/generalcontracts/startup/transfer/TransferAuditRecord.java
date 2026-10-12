package ai.generalcontracts.startup.transfer;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "transfer_audit")
@CompoundIndex(name = "transfer_audit_timeline", def = "{'instructionId': 1, 'occurredAt': 1}")
public class TransferAuditRecord {
    @Id
    private String eventId;
    private String instructionId;
    private String correlationId;
    private String eventType;
    private String status;
    private String reasonCode;
    private String actor;
    private Instant occurredAt;

    public TransferAuditRecord() {
    }

    public TransferAuditRecord(
        String eventId,
        String instructionId,
        String correlationId,
        String eventType,
        String status,
        String reasonCode,
        String actor,
        Instant occurredAt
    ) {
        this.eventId = eventId;
        this.instructionId = instructionId;
        this.correlationId = correlationId;
        this.eventType = eventType;
        this.status = status;
        this.reasonCode = reasonCode;
        this.actor = actor;
        this.occurredAt = occurredAt;
    }

    public String getEventId() { return eventId; }
    public String getInstructionId() { return instructionId; }
    public String getCorrelationId() { return correlationId; }
    public String getEventType() { return eventType; }
    public String getStatus() { return status; }
    public String getReasonCode() { return reasonCode; }
    public String getActor() { return actor; }
    public Instant getOccurredAt() { return occurredAt; }
}
