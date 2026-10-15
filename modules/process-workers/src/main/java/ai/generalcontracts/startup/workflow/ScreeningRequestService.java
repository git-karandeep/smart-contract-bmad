package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.startup.transfer.ScreeningRequestRecord;
import ai.generalcontracts.startup.transfer.ScreeningRequestRepository;
import ai.generalcontracts.startup.transfer.ScreeningRequestStatus;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ScreeningRequestService {
    private final ScreeningRequestRepository requests;
    private final ScreeningRequestPolicyResolver policyResolver;
    private final ScreeningRequestPublisher publisher;
    private final Clock clock;

    @Autowired
    public ScreeningRequestService(
        ScreeningRequestRepository requests,
        ScreeningRequestPolicyResolver policyResolver,
        ScreeningRequestPublisher publisher
    ) {
        this(requests, policyResolver, publisher, Clock.systemUTC());
    }

    ScreeningRequestService(
        ScreeningRequestRepository requests,
        ScreeningRequestPolicyResolver policyResolver,
        ScreeningRequestPublisher publisher,
        Clock clock
    ) {
        this.requests = requests;
        this.policyResolver = policyResolver;
        this.publisher = publisher;
        this.clock = clock;
    }

    public RequestOutcome createAndPublish(TransferInstruction instruction, ScreeningType type) {
        if (type == ScreeningType.KYC) {
            return createKycRequests(instruction);
        }
        String requestId = requestId(instruction.getInstructionId(), type);
        Optional<ScreeningRequestRecord> existing = requests.findByRequestId(requestId);
        if (existing.isPresent()) {
            return publishIfPending(existing.get());
        }

        Instant now = clock.instant();
        Optional<ScreeningRequestPolicy> policy;
        try {
            policy = policyResolver.resolve(instruction, type);
        } catch (RuntimeException unavailable) {
            policy = Optional.empty();
        }
        ScreeningRequestRecord request;
        if (policy.isEmpty()) {
            request = new ScreeningRequestRecord(
                requestId,
                correlationId(instruction.getInstructionId(), type),
                instruction.getInstructionId(),
                null,
                type.name(),
                null,
                now,
                ScreeningRequestStatus.POLICY_UNAVAILABLE,
                "SCREENING_POLICY_UNAVAILABLE"
            );
        } else {
            ScreeningRequestPolicy selected = policy.get();
            request = new ScreeningRequestRecord(
                requestId,
                correlationId(instruction.getInstructionId(), type),
                instruction.getInstructionId(),
                selected.providerId(),
                type.name(),
                selected.screeningVersion(),
                now
            );
        }

        try {
            request = requests.insert(request);
        } catch (DuplicateKeyException duplicate) {
            request = requests.findByRequestId(requestId).orElseThrow(() -> duplicate);
        }
        return publishIfPending(request);
    }

    public RequestOutcome createKycRequests(TransferInstruction instruction) {
        String instructionId = instruction.getInstructionId();
        List<ScreeningRequestRecord> existing =
            requests.findAllByInstructionIdAndScreeningType(instructionId, ScreeningType.KYC.name());
        if (!existing.isEmpty()) {
            if (existing.size() == 1
                && ScreeningRequestStatus.NOT_REQUIRED.name().equals(existing.get(0).getPublicationStatus())) {
                return new RequestOutcome(true, existing.get(0).getRequestId(), null, List.of(), true);
            }
            if (existing.size() == 1 && existing.get(0).getSubjectReference() == null) {
                return notReady(existing.get(0));
            }
            return publishKycRequests(existing);
        }

        Optional<KycScreeningPolicy> selected;
        try {
            selected = policyResolver.resolveKyc(instruction);
        } catch (RuntimeException unavailable) {
            selected = Optional.empty();
        }
        if (selected == null || selected.isEmpty() || selected.get() == null) {
            ScreeningRequestRecord unavailable = persistKycMarker(
                instructionId, ScreeningRequestStatus.POLICY_UNAVAILABLE, "SCREENING_POLICY_UNAVAILABLE"
            );
            return notReady(unavailable);
        }

        KycScreeningPolicy policy = selected.get();
        ScreeningRequestStatus invalidState = validateKycPolicy(policy);
        if (invalidState != null) {
            String reason = invalidState == ScreeningRequestStatus.POLICY_UNAVAILABLE
                ? "SCREENING_POLICY_UNAVAILABLE"
                : "KYC_SUBJECT_DATA_INVALID";
            return notReady(persistKycMarker(instructionId, invalidState, reason, policy.policyVersion()));
        }
        if (policy.requirement() == KycScreeningPolicy.Requirement.NOT_REQUIRED) {
            ScreeningRequestRecord notRequired = persistKycMarker(
                instructionId, ScreeningRequestStatus.NOT_REQUIRED, null, policy.policyVersion()
            );
            return new RequestOutcome(true, notRequired.getRequestId(), null, List.of(), true);
        }

        Instant now = clock.instant();
        int subjectCount = policy.subjects().size();
        List<ScreeningRequestRecord> created = new ArrayList<>(subjectCount);
        for (KycScreeningPolicy.Subject subject : policy.subjects()) {
            String requestId = kycRequestId(instructionId, subject.subjectReference());
            created.add(new ScreeningRequestRecord(
                requestId,
                requestId + ":correlation",
                instructionId,
                policy.providerId(),
                ScreeningType.KYC.name(),
                policy.providerVersion(),
                now,
                subject.subjectReference(),
                subject.authorizedReferences(),
                policy.policyVersion(),
                subjectCount
            ));
        }

        try {
            for (ScreeningRequestRecord request : created) {
                requests.insert(request);
            }
        } catch (DuplicateKeyException duplicate) {
            List<ScreeningRequestRecord> raced =
                requests.findAllByInstructionIdAndScreeningType(instructionId, ScreeningType.KYC.name());
            if (raced.isEmpty()) {
                throw duplicate;
            }
            return publishKycRequests(raced);
        }
        return publishKycRequests(created);
    }

    public ScreeningRequestRecord recordTimeout(String instructionId, ScreeningType type) {
        if (type == ScreeningType.KYC) {
            List<ScreeningRequestRecord> kycRequests =
                requests.findAllByInstructionIdAndScreeningType(instructionId, type.name());
            if (kycRequests.isEmpty()) {
                throw new IllegalStateException("Screening request is not durably recorded");
            }
            ScreeningRequestRecord first = kycRequests.get(0);
            for (ScreeningRequestRecord request : kycRequests) {
                if (ScreeningRequestStatus.PENDING.name().equals(request.getPublicationStatus())
                    || ScreeningRequestStatus.PUBLISHED.name().equals(request.getPublicationStatus())) {
                    request.markTimedOut(clock.instant());
                    requests.save(request);
                }
            }
            return first;
        }
        String requestId = requestId(instructionId, type);
        return recordTimeout(instructionId, type, requestId);
    }

    public ScreeningRequestRecord recordTimeout(String instructionId, ScreeningType type, String requestId) {
        ScreeningRequestRecord request = requests.findByRequestId(requestId)
            .orElseThrow(() -> new IllegalStateException("Screening request is not durably recorded"));
        if (!instructionId.equals(request.getInstructionId()) || !type.name().equals(request.getScreeningType())) {
            throw new IllegalStateException("Screening request does not match its timeout");
        }
        if (ScreeningRequestStatus.PENDING.name().equals(request.getPublicationStatus())
            || ScreeningRequestStatus.PUBLISHED.name().equals(request.getPublicationStatus())) {
            request.markTimedOut(clock.instant());
            request = requests.save(request);
        }
        return request;
    }

    private RequestOutcome publishKycRequests(List<ScreeningRequestRecord> kycRequests) {
        if (kycRequests.size() == 1
            && ScreeningRequestStatus.NOT_REQUIRED.name().equals(kycRequests.get(0).getPublicationStatus())) {
            return new RequestOutcome(true, kycRequests.get(0).getRequestId(), null, List.of(), true);
        }
        if (kycRequests.isEmpty()) {
            return new RequestOutcome(false, null, "KYC_SUBJECT_SET_INCOMPLETE", List.of(), false);
        }
        Integer expectedCount = kycRequests.get(0).getRequiredSubjectCount();
        if (expectedCount == null || expectedCount != kycRequests.size()
            || kycRequests.stream().anyMatch(request ->
                !expectedCount.equals(request.getRequiredSubjectCount())
                    || request.getSubjectReference() == null
                    || request.getAuthorizedReferences() == null)) {
            ScreeningRequestRecord hold = persistKycMarker(
                kycRequests.get(0).getInstructionId(),
                ScreeningRequestStatus.INVALID_SUBJECT_DATA,
                "KYC_SUBJECT_SET_INCOMPLETE"
            );
            return notReady(hold);
        }

        List<String> publishedIds = new ArrayList<>();
        for (ScreeningRequestRecord request : kycRequests) {
            RequestOutcome outcome = publishIfPending(request);
            if (!outcome.ready()) {
                return new RequestOutcome(
                    false, request.getRequestId(), outcome.reasonCode(), List.copyOf(publishedIds), false
                );
            }
            publishedIds.add(request.getRequestId());
        }
        return new RequestOutcome(true, publishedIds.get(0), null, publishedIds, false);
    }

    private ScreeningRequestRecord persistKycMarker(
        String instructionId,
        ScreeningRequestStatus status,
        String reasonCode
    ) {
        return persistKycMarker(instructionId, status, reasonCode, null);
    }

    private ScreeningRequestRecord persistKycMarker(
        String instructionId,
        ScreeningRequestStatus status,
        String reasonCode,
        String policyVersion
    ) {
        ScreeningRequestRecord marker = new ScreeningRequestRecord(
            requestId(instructionId, ScreeningType.KYC),
            correlationId(instructionId, ScreeningType.KYC),
            instructionId,
            null,
            ScreeningType.KYC.name(),
            null,
            clock.instant(),
            null,
            null,
            policyVersion,
            null,
            status,
            reasonCode
        );
        try {
            return requests.insert(marker);
        } catch (DuplicateKeyException duplicate) {
            return requests.findByRequestId(marker.getRequestId()).orElseThrow(() -> duplicate);
        }
    }

    private RequestOutcome notReady(ScreeningRequestRecord request) {
        return new RequestOutcome(false, request.getRequestId(), request.getReasonCode(), List.of(), false);
    }

    private ScreeningRequestStatus validateKycPolicy(KycScreeningPolicy policy) {
        if (policy.requirement() == null || blank(policy.policyVersion())) {
            return ScreeningRequestStatus.POLICY_UNAVAILABLE;
        }
        if (policy.requirement() == KycScreeningPolicy.Requirement.NOT_REQUIRED) {
            return policy.subjects() != null && policy.subjects().isEmpty()
                ? null
                : ScreeningRequestStatus.INVALID_SUBJECT_DATA;
        }
        if (blank(policy.providerId()) || blank(policy.providerVersion())) {
            return ScreeningRequestStatus.POLICY_UNAVAILABLE;
        }
        if (policy.subjects() == null || policy.subjects().isEmpty()) {
            return ScreeningRequestStatus.INVALID_SUBJECT_DATA;
        }
        Set<String> subjectReferences = new HashSet<>();
        for (KycScreeningPolicy.Subject subject : policy.subjects()) {
            if (subject == null || blank(subject.subjectReference())
                || subject.authorizedReferences() == null || subject.authorizedReferences().isEmpty()
                || subject.authorizedReferences().entrySet().stream().anyMatch(entry ->
                    blank(entry.getKey()) || blank(entry.getValue()))
                || !subjectReferences.add(subject.subjectReference())) {
                return ScreeningRequestStatus.INVALID_SUBJECT_DATA;
            }
        }
        return null;
    }

    private RequestOutcome publishIfPending(ScreeningRequestRecord request) {
        if (ScreeningRequestStatus.PUBLISHED.name().equals(request.getPublicationStatus())) {
            return new RequestOutcome(true, request.getRequestId(), null);
        }
        if (!ScreeningRequestStatus.PENDING.name().equals(request.getPublicationStatus())) {
            return new RequestOutcome(false, request.getRequestId(), request.getReasonCode());
        }

        ScreeningRequestMessage message = new ScreeningRequestMessage(
            request.getInstructionId(),
            request.getRequestId(),
            request.getCorrelationId(),
            request.getProviderId(),
            ScreeningType.valueOf(request.getScreeningType()),
            request.getScreeningVersion(),
            request.getRequestedAt(),
            request.getSubjectReference(),
            request.getAuthorizedReferences(),
            request.getPolicyVersion()
        );
        try {
            publisher.publish(message);
        } catch (RuntimeException publishFailure) {
            request.markPublicationFailed();
            requests.save(request);
            return new RequestOutcome(false, request.getRequestId(), request.getReasonCode());
        }
        request.markPublished(clock.instant());
        requests.save(request);
        return new RequestOutcome(true, request.getRequestId(), null);
    }

    public static String requestId(String instructionId, ScreeningType type) {
        return instructionId + ":" + type.name();
    }

    public static String correlationId(String instructionId, ScreeningType type) {
        return instructionId + ":" + type.name();
    }

    private String kycRequestId(String instructionId, String subjectReference) {
        String identity = instructionId + "\u0000KYC\u0000" + subjectReference;
        String stableId;
        try {
            stableId = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        return instructionId + ":KYC:" + stableId;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record RequestOutcome(
        boolean ready,
        String requestId,
        String reasonCode,
        List<String> requestIds,
        boolean notRequired
    ) {
        public RequestOutcome {
            requestIds = requestIds == null ? List.of() : List.copyOf(requestIds);
        }

        public RequestOutcome(boolean ready, String requestId, String reasonCode) {
            this(ready, requestId, reasonCode, requestId == null ? List.of() : List.of(requestId), false);
        }
    }
}
