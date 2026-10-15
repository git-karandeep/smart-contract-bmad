package ai.generalcontracts.startup.transfer;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "screening_requests")
@CompoundIndex(
    name = "screening_instruction_type_subject_unique",
    def = "{'instructionId': 1, 'screeningType': 1, 'subjectReference': 1}",
    unique = true
)
public class ScreeningRequestRecord {
    @Id
    private String requestId;
    private String correlationId;
    private String instructionId;
    private String providerId;
    private String screeningType;
    private String screeningVersion;
    private Instant requestedAt;
    private String subjectReference;
    private Map<String, String> authorizedReferences;
    private String policyVersion;
    private Integer requiredSubjectCount;
    private String publicationStatus;
    private String reasonCode;
    private Instant publishedAt;
    private Instant timedOutAt;
    @Version
    private Long version;

    public ScreeningRequestRecord() {
    }

    public ScreeningRequestRecord(
        String requestId,
        String correlationId,
        String instructionId,
        String providerId,
        String screeningType,
        String screeningVersion,
        Instant requestedAt
    ) {
        this.requestId = requestId;
        this.correlationId = correlationId;
        this.instructionId = instructionId;
        this.providerId = providerId;
        this.screeningType = screeningType;
        this.screeningVersion = screeningVersion;
        this.requestedAt = requestedAt;
        this.publicationStatus = ScreeningRequestStatus.PENDING.name();
    }

    public ScreeningRequestRecord(
        String requestId,
        String correlationId,
        String instructionId,
        String providerId,
        String screeningType,
        String screeningVersion,
        Instant requestedAt,
        String subjectReference,
        Map<String, String> authorizedReferences,
        String policyVersion,
        Integer requiredSubjectCount
    ) {
        this(requestId, correlationId, instructionId, providerId, screeningType, screeningVersion, requestedAt);
        this.subjectReference = subjectReference;
        this.authorizedReferences = authorizedReferences == null ? null : Map.copyOf(authorizedReferences);
        this.policyVersion = policyVersion;
        this.requiredSubjectCount = requiredSubjectCount;
    }

    public ScreeningRequestRecord(
        String requestId,
        String correlationId,
        String instructionId,
        String providerId,
        String screeningType,
        String screeningVersion,
        Instant requestedAt,
        String subjectReference,
        Map<String, String> authorizedReferences,
        String policyVersion,
        Integer requiredSubjectCount,
        ScreeningRequestStatus publicationStatus,
        String reasonCode
    ) {
        this(
            requestId,
            correlationId,
            instructionId,
            providerId,
            screeningType,
            screeningVersion,
            requestedAt,
            subjectReference,
            authorizedReferences,
            policyVersion,
            requiredSubjectCount
        );
        this.publicationStatus = publicationStatus.name();
        this.reasonCode = reasonCode;
    }

    public ScreeningRequestRecord(
        String requestId,
        String correlationId,
        String instructionId,
        String providerId,
        String screeningType,
        String screeningVersion,
        Instant requestedAt,
        ScreeningRequestStatus publicationStatus,
        String reasonCode
    ) {
        this(requestId, correlationId, instructionId, providerId, screeningType, screeningVersion, requestedAt);
        this.publicationStatus = publicationStatus.name();
        this.reasonCode = reasonCode;
    }

    public String getRequestId() { return requestId; }
    public String getCorrelationId() { return correlationId; }
    public String getInstructionId() { return instructionId; }
    public String getProviderId() { return providerId; }
    public String getScreeningType() { return screeningType; }
    public String getScreeningVersion() { return screeningVersion; }
    public Instant getRequestedAt() { return requestedAt; }
    public String getSubjectReference() { return subjectReference; }
    public Map<String, String> getAuthorizedReferences() {
        return authorizedReferences == null ? null : Map.copyOf(authorizedReferences);
    }
    public String getPolicyVersion() { return policyVersion; }
    public Integer getRequiredSubjectCount() { return requiredSubjectCount; }
    public String getPublicationStatus() { return publicationStatus; }
    public String getReasonCode() { return reasonCode; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getTimedOutAt() { return timedOutAt; }
    public Long getVersion() { return version; }

    public void markPublished(Instant at) {
        this.publicationStatus = ScreeningRequestStatus.PUBLISHED.name();
        this.reasonCode = null;
        this.publishedAt = at;
    }

    public void markPolicyUnavailable() {
        this.publicationStatus = ScreeningRequestStatus.POLICY_UNAVAILABLE.name();
        this.reasonCode = "SCREENING_POLICY_UNAVAILABLE";
    }

    public void markInvalidSubjectData() {
        this.publicationStatus = ScreeningRequestStatus.INVALID_SUBJECT_DATA.name();
        this.reasonCode = "KYC_SUBJECT_DATA_INVALID";
    }

    public void markNotRequired() {
        this.publicationStatus = ScreeningRequestStatus.NOT_REQUIRED.name();
        this.reasonCode = null;
    }

    public void markPublicationFailed() {
        this.publicationStatus = ScreeningRequestStatus.PUBLICATION_FAILED.name();
        this.reasonCode = "SCREENING_REQUEST_PUBLICATION_FAILED";
    }

    public void markTimedOut(Instant at) {
        this.publicationStatus = ScreeningRequestStatus.TIMED_OUT.name();
        this.reasonCode = "SCREENING_RESULT_TIMEOUT";
        this.timedOutAt = at;
    }
}
