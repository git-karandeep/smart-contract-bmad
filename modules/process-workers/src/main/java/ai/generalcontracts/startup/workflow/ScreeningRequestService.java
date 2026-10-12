package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.startup.transfer.ScreeningRequestRecord;
import ai.generalcontracts.startup.transfer.ScreeningRequestRepository;
import ai.generalcontracts.startup.transfer.ScreeningRequestStatus;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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

    public ScreeningRequestRecord recordTimeout(String instructionId, ScreeningType type) {
        String requestId = requestId(instructionId, type);
        ScreeningRequestRecord request = requests.findByRequestId(requestId)
            .orElseThrow(() -> new IllegalStateException("Screening request is not durably recorded"));
        if (ScreeningRequestStatus.PENDING.name().equals(request.getPublicationStatus())
            || ScreeningRequestStatus.PUBLISHED.name().equals(request.getPublicationStatus())) {
            request.markTimedOut(clock.instant());
            request = requests.save(request);
        }
        return request;
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
            request.getRequestedAt()
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

    public record RequestOutcome(boolean ready, String requestId, String reasonCode) {
    }
}
