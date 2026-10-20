package ai.generalcontracts.startup.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import ai.generalcontracts.startup.workflow.ScreeningDecision;
import ai.generalcontracts.startup.workflow.ScreeningResult;
import ai.generalcontracts.startup.workflow.ScreeningResultAuthenticator;
import ai.generalcontracts.startup.workflow.ScreeningResultValidator;
import ai.generalcontracts.startup.workflow.ScreeningType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ScreeningResultIngestionServiceTest {
    @Mock private ScreeningRequestRepository requests;
    @Mock private ScreeningResultRepository results;
    @Mock private TransferInstructionRepository instructions;
    @Mock private ScreeningResultAuthenticator authenticator;
    @Mock private ScreeningWorkflowCorrelator correlator;

    private ScreeningResultIngestionService service;
    private final ScreeningRequestRecord expected = new ScreeningRequestRecord(
        "request-1", "transfer-1:KYC", "transfer-1", "provider-1", "KYC", "v1",
        Instant.parse("2026-10-11T20:00:00Z")
    );

    @BeforeEach
    void setUp() {
        service = new ScreeningResultIngestionService(
            requests, results, instructions, authenticator, new ScreeningResultValidator(), correlator,
            Clock.fixed(Instant.parse("2026-10-11T21:00:00Z"), ZoneOffset.UTC)
        );
        lenient().when(instructions.findById("transfer-1"))
            .thenReturn(Optional.of(instruction(TransferStatus.SCREENING_PENDING)));
    }

    @Test
    void persistsThenCorrelatesValidAuthenticatedDecision() {
        when(authenticator.isAuthenticated(result(), "valid-signature")).thenReturn(true);
        when(requests.findByRequestId("request-1")).thenReturn(Optional.of(expected));
        when(results.findByProviderIdAndMessageId("provider-1", "message-1")).thenReturn(Optional.empty());
        when(results.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(
            ScreeningResultIngestionService.IngestionOutcome.APPLIED,
            service.ingest(result(), "valid-signature")
        );
        verify(results, times(2)).save(any(ScreeningResultRecord.class));
        verify(correlator).correlate(result());
    }

    @Test
    void persistsAndCorrelatesMultipleKycResultsByTheirOwnRequestIds() {
        ScreeningRequestRecord firstRequest = new ScreeningRequestRecord(
            "request-kyc-a", "corr-kyc-a", "transfer-1", "provider-1", "KYC", "provider-v2",
            Instant.parse("2026-10-11T20:00:00Z"), "subject-a", java.util.Map.of("caseRef", "opaque-a"),
            "policy-v4", 2
        );
        ScreeningRequestRecord secondRequest = new ScreeningRequestRecord(
            "request-kyc-b", "corr-kyc-b", "transfer-1", "provider-1", "KYC", "provider-v2",
            Instant.parse("2026-10-11T20:00:00Z"), "subject-b", java.util.Map.of("caseRef", "opaque-b"),
            "policy-v4", 2
        );
        ScreeningResult first = kycResult("message-a", "request-kyc-a", "corr-kyc-a");
        ScreeningResult second = kycResult("message-b", "request-kyc-b", "corr-kyc-b");
        when(authenticator.isAuthenticated(any(), org.mockito.ArgumentMatchers.eq("valid"))).thenReturn(true);
        when(requests.findByRequestId("request-kyc-a")).thenReturn(Optional.of(firstRequest));
        when(requests.findByRequestId("request-kyc-b")).thenReturn(Optional.of(secondRequest));
        when(results.findByProviderIdAndMessageId("provider-1", "message-a")).thenReturn(Optional.empty());
        when(results.findByProviderIdAndMessageId("provider-1", "message-b")).thenReturn(Optional.empty());
        when(results.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(ScreeningResultIngestionService.IngestionOutcome.APPLIED, service.ingest(first, "valid"));
        assertEquals(ScreeningResultIngestionService.IngestionOutcome.APPLIED, service.ingest(second, "valid"));

        verify(correlator).correlate(first);
        verify(correlator).correlate(second);
    }

    @Test
    void rejectsMalformedUnknownAndNeverDispatchedRequestsWithoutPersisting() {
        assertThrows(ResponseStatusException.class, () -> service.ingest(null, "valid"));

        ScreeningResult unknown = new ScreeningResult(
            "provider-1", "message-9", "transfer-1", "missing-request", "transfer-1:KYC",
            ScreeningType.KYC, ScreeningDecision.APPROVED, result().screenedAt()
        );
        when(authenticator.isAuthenticated(unknown, "valid")).thenReturn(true);
        when(requests.findByRequestId("missing-request")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.ingest(unknown, "valid"));

        ScreeningRequestRecord policyUnavailable = new ScreeningRequestRecord(
            "request-1", "transfer-1:KYC", "transfer-1", null, "KYC", null,
            Instant.parse("2026-10-11T20:00:00Z"), ScreeningRequestStatus.POLICY_UNAVAILABLE,
            "SCREENING_POLICY_UNAVAILABLE"
        );
        when(authenticator.isAuthenticated(result(), "valid")).thenReturn(true);
        when(requests.findByRequestId("request-1")).thenReturn(Optional.of(policyUnavailable));
        assertThrows(ResponseStatusException.class, () -> service.ingest(result(), "valid"));

        verify(results, never()).save(any());
        verify(correlator, never()).correlate(any());
    }

    @Test
    void rejectsUnauthenticatedAndMismatchedResultsBeforePersistence() {
        when(authenticator.isAuthenticated(result(), "invalid")).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> service.ingest(result(), "invalid"));
        verify(results, never()).save(any());

        ScreeningResult mismatched = new ScreeningResult(
            "provider-1", "message-2", "other-instruction", "request-1", "transfer-1:KYC",
            ScreeningType.KYC, ScreeningDecision.APPROVED, result().screenedAt()
        );
        when(authenticator.isAuthenticated(mismatched, "valid")).thenReturn(true);
        when(requests.findByRequestId("request-1")).thenReturn(Optional.of(expected));
        assertThrows(ResponseStatusException.class, () -> service.ingest(mismatched, "valid"));
        verify(results, never()).save(any());
    }

    @Test
    void doesNotReapplyAlreadyCorrelatedDuplicate() {
        ScreeningResultRecord existing = new ScreeningResultRecord(
            "id", "provider-1", "message-1", "transfer-1", "request-1",
            "transfer-1:KYC", "KYC", "APPROVED", result().screenedAt(), Instant.now()
        );
        existing.setCorrelatedAt(Instant.now());
        when(authenticator.isAuthenticated(result(), "valid")).thenReturn(true);
        when(requests.findByRequestId("request-1")).thenReturn(Optional.of(expected));
        when(results.findByProviderIdAndMessageId("provider-1", "message-1")).thenReturn(Optional.of(existing));

        assertEquals(
            ScreeningResultIngestionService.IngestionOutcome.DUPLICATE,
            service.ingest(result(), "valid")
        );
        verify(correlator, never()).correlate(any());
    }

    @Test
    void rejectsReplayedMessageIdentityWithConflictingDecision() {
        ScreeningResultRecord existing = new ScreeningResultRecord(
            "id", "provider-1", "message-1", "transfer-1", "request-1",
            "transfer-1:KYC", "KYC", "APPROVED", result().screenedAt(), Instant.now()
        );
        when(authenticator.isAuthenticated(any(), org.mockito.ArgumentMatchers.eq("valid"))).thenReturn(true);
        when(requests.findByRequestId("request-1")).thenReturn(Optional.of(expected));
        when(results.findByProviderIdAndMessageId("provider-1", "message-1")).thenReturn(Optional.of(existing));
        ScreeningResult changed = new ScreeningResult(
            "provider-1", "message-1", "transfer-1", "request-1", "transfer-1:KYC",
            ScreeningType.KYC, ScreeningDecision.REJECTED, result().screenedAt()
        );

        assertThrows(ResponseStatusException.class, () -> service.ingest(changed, "valid"));
        verify(correlator, never()).correlate(any());
    }

    @Test
    void rejectsASecondDistinctMessageForAnAlreadyDecidedScreeningRequest() {
        ScreeningResult anotherMessage = new ScreeningResult(
            "provider-1", "message-2", "transfer-1", "request-1", "transfer-1:KYC",
            ScreeningType.KYC, ScreeningDecision.REJECTED, result().screenedAt()
        );
        when(authenticator.isAuthenticated(anotherMessage, "valid")).thenReturn(true);
        when(requests.findByRequestId("request-1")).thenReturn(Optional.of(expected));
        when(results.findByProviderIdAndMessageId("provider-1", "message-2"))
            .thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new org.springframework.dao.DuplicateKeyException("request already decided"))
            .when(results).save(any(ScreeningResultRecord.class));

        assertThrows(ResponseStatusException.class, () -> service.ingest(anotherMessage, "valid"));
        verify(correlator, never()).correlate(any());
    }

    @Test
    void storesLateResultWithoutCorrelatingHeldInstruction() {
        when(instructions.findById("transfer-1")).thenReturn(Optional.of(instruction(TransferStatus.COMPLIANCE_HOLD)));
        when(authenticator.isAuthenticated(result(), "valid")).thenReturn(true);
        when(requests.findByRequestId("request-1")).thenReturn(Optional.of(expected));
        when(results.findByProviderIdAndMessageId("provider-1", "message-1")).thenReturn(Optional.empty());
        when(results.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(
            ScreeningResultIngestionService.IngestionOutcome.LATE,
            service.ingest(result(), "valid")
        );
        verify(correlator, never()).correlate(any());
        verify(results).save(org.mockito.ArgumentMatchers.argThat(
            record -> "LATE".equals(record.getCorrelationDisposition())
        ));
    }

    @Test
    void lateDuplicateCannotBeCorrelatedAfterTransferReopens() {
        ScreeningResultRecord late = new ScreeningResultRecord(
            "id", "provider-1", "message-1", "transfer-1", "request-1",
            "transfer-1:KYC", "KYC", "APPROVED", result().screenedAt(), Instant.now()
        );
        late.setCorrelationDisposition("LATE");
        when(authenticator.isAuthenticated(result(), "valid")).thenReturn(true);
        when(requests.findByRequestId("request-1")).thenReturn(Optional.of(expected));
        when(results.findByProviderIdAndMessageId("provider-1", "message-1")).thenReturn(Optional.of(late));

        assertEquals(
            ScreeningResultIngestionService.IngestionOutcome.DUPLICATE,
            service.ingest(result(), "valid")
        );
        verify(correlator, never()).correlate(any());
    }

    private TransferInstruction instruction(TransferStatus status) {
        TransferInstruction instruction = new TransferInstruction();
        instruction.setInstructionId("transfer-1");
        instruction.setStatus(status);
        return instruction;
    }

    private ScreeningResult result() {
        return new ScreeningResult(
            "provider-1", "message-1", "transfer-1", "request-1", "transfer-1:KYC",
            ScreeningType.KYC, ScreeningDecision.APPROVED, Instant.parse("2026-10-11T20:30:00Z")
        );
    }

    private ScreeningResult kycResult(String messageId, String requestId, String correlationId) {
        return new ScreeningResult(
            "provider-1",
            messageId,
            "transfer-1",
            requestId,
            correlationId,
            ScreeningType.KYC,
            ScreeningDecision.APPROVED,
            Instant.parse("2026-10-11T20:30:00Z")
        );
    }
}
