package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.startup.workflow.ScreeningResult;
import ai.generalcontracts.startup.workflow.ScreeningResultAuthenticator;
import ai.generalcontracts.startup.workflow.ScreeningResultValidator;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScreeningResultIngestionService {
    private final ScreeningRequestRepository requests;
    private final ScreeningResultRepository results;
    private final TransferInstructionRepository instructions;
    private final ScreeningResultAuthenticator authenticator;
    private final ScreeningResultValidator validator;
    private final ScreeningWorkflowCorrelator correlator;
    private final Clock clock;

    @Autowired
    public ScreeningResultIngestionService(
        ScreeningRequestRepository requests,
        ScreeningResultRepository results,
        TransferInstructionRepository instructions,
        ScreeningResultAuthenticator authenticator,
        ScreeningResultValidator validator,
        ScreeningWorkflowCorrelator correlator
    ) {
        this(requests, results, instructions, authenticator, validator, correlator, Clock.systemUTC());
    }

    ScreeningResultIngestionService(
        ScreeningRequestRepository requests,
        ScreeningResultRepository results,
        TransferInstructionRepository instructions,
        ScreeningResultAuthenticator authenticator,
        ScreeningResultValidator validator,
        ScreeningWorkflowCorrelator correlator,
        Clock clock
    ) {
        this.requests = requests;
        this.results = results;
        this.instructions = instructions;
        this.authenticator = authenticator;
        this.validator = validator;
        this.correlator = correlator;
        this.clock = clock;
    }

    public IngestionOutcome ingest(ScreeningResult result, String signature) {
        if (!authenticator.isAuthenticated(result, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Screening provider authentication failed");
        }
        var expected = requests.findByRequestId(result.screeningRequestId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Screening request not found"));
        try {
            validator.validate(result, expected);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Screening result correlation is invalid");
        }
        TransferInstruction instruction = instructions.findById(expected.getInstructionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Screening request not found"));

        Optional<ScreeningResultRecord> existing =
            results.findByProviderIdAndMessageId(result.providerId(), result.messageId());
        boolean lateAtInsert = existing.isEmpty() && instruction.getStatus() != TransferStatus.SCREENING_PENDING;
        ScreeningResultRecord record = existing.orElseGet(() -> saveOnce(newRecord(result, lateAtInsert)));
        if (!matches(record, result)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Screening message identity conflicts with its stored result");
        }
        if ("LATE".equals(record.getCorrelationDisposition())) {
            return lateAtInsert ? IngestionOutcome.LATE : IngestionOutcome.DUPLICATE;
        }
        if (instruction.getStatus() != TransferStatus.SCREENING_PENDING) {
            record.setCorrelationDisposition("LATE");
            results.save(record);
            return IngestionOutcome.LATE;
        }
        if (record.getCorrelatedAt() == null) {
            correlator.correlate(result);
            record.setCorrelatedAt(clock.instant());
            record.setCorrelationDisposition("CORRELATED");
            results.save(record);
            return IngestionOutcome.APPLIED;
        }

        return IngestionOutcome.DUPLICATE;
    }

    private boolean matches(ScreeningResultRecord record, ScreeningResult result) {
        return Objects.equals(record.getInstructionId(), result.instructionId())
            && Objects.equals(record.getRequestId(), result.screeningRequestId())
            && Objects.equals(record.getCorrelationId(), result.correlationId())
            && Objects.equals(record.getScreeningType(), result.screeningType().name())
            && Objects.equals(record.getDecision(), result.decision().name())
            && Objects.equals(record.getScreenedAt(), result.screenedAt());
    }

    private ScreeningResultRecord newRecord(ScreeningResult result, boolean late) {
        ScreeningResultRecord record = new ScreeningResultRecord(
            UUID.randomUUID().toString(),
            result.providerId(),
            result.messageId(),
            result.instructionId(),
            result.screeningRequestId(),
            result.correlationId(),
            result.screeningType().name(),
            result.decision().name(),
            result.screenedAt(),
            Instant.now(clock)
        );
        if (late) {
            record.setCorrelationDisposition("LATE");
        }
        return record;
    }

    private ScreeningResultRecord saveOnce(ScreeningResultRecord record) {
        try {
            return results.save(record);
        } catch (DuplicateKeyException duplicate) {
            return results.findByProviderIdAndMessageId(record.getProviderId(), record.getMessageId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.CONFLICT, "Screening request already has a stored result", duplicate
                ));
        }
    }

    public enum IngestionOutcome {
        APPLIED,
        LATE,
        DUPLICATE
    }
}
