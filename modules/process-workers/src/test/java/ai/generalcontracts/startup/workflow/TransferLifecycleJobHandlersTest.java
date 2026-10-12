package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.generalcontracts.chain.TransferSubmission;
import ai.generalcontracts.startup.transfer.ScreeningRequestRecord;
import ai.generalcontracts.startup.transfer.ScreeningRequestRepository;
import ai.generalcontracts.startup.transfer.ScreeningRequestStatus;
import ai.generalcontracts.startup.transfer.ScreeningResultRecord;
import ai.generalcontracts.startup.transfer.ScreeningResultRepository;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import ai.generalcontracts.startup.transfer.TransferInstructionRepository;
import ai.generalcontracts.startup.transfer.TransferLifecycleService;
import ai.generalcontracts.startup.transfer.TransferStatus;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TransferLifecycleJobHandlersTest {
    @Test
    void missingValidationPolicyPersistsFailureAndCannotReachSubmission() {
        Fixture fixture = new Fixture(TransferStatus.RECEIVED);
        fixture.validationPolicy = instruction ->
            TransferValidationPolicy.ValidationDecision.rejected("TRANSFER_VALIDATION_POLICY_UNAVAILABLE");
        TransferLifecycleJobHandlers handlers = fixture.handlers();

        Map<String, Object> validation = handlers.validate("transfer-1");
        Map<String, Object> submission = handlers.submit("transfer-1", true);

        assertEquals(false, validation.get("validationPassed"));
        assertEquals(TransferStatus.VALIDATION_FAILED, fixture.instruction.getStatus());
        assertEquals("UNKNOWN", submission.get("submissionOutcome"));
        verify(fixture.submissionPort, never()).submit(any());
    }

    @Test
    void missingScreeningPolicyIsDurablyHeldAndCannotReachSubmission() {
        Fixture fixture = new Fixture(TransferStatus.SCREENING_PENDING);
        ScreeningRequestRepository requestRepository = fixture.requests;
        ScreeningRequestPolicyResolver missingPolicy = (instruction, type) -> Optional.empty();
        ScreeningRequestPublisher publisher = mock(ScreeningRequestPublisher.class);
        ScreeningRequestService requestService = new ScreeningRequestService(
            requestRepository, missingPolicy, publisher
        );
        fixture.screeningRequests = requestService;
        when(requestRepository.findByRequestId("transfer-1:KYC")).thenReturn(Optional.empty());
        when(requestRepository.insert(any(ScreeningRequestRecord.class)))
            .thenAnswer(call -> call.getArgument(0));
        when(requestRepository.findByInstructionIdAndScreeningType("transfer-1", "KYC"))
            .thenAnswer(call -> requestRepository.findByRequestId("transfer-1:KYC"));
        when(requestRepository.findByInstructionIdAndScreeningType("transfer-1", "AML"))
            .thenReturn(Optional.empty());
        when(requestRepository.findByInstructionIdAndScreeningType("transfer-1", "SANCTIONS"))
            .thenReturn(Optional.empty());

        TransferLifecycleJobHandlers handlers = fixture.handlers();
        Map<String, Object> request = handlers.requestScreening("transfer-1", ScreeningType.KYC);
        Map<String, Object> aggregate = handlers.aggregateCompliance("transfer-1");
        Map<String, Object> gate = handlers.preExecutionGate(
            "transfer-1", (String) aggregate.get("complianceOutcome")
        );
        Map<String, Object> submission = handlers.submit("transfer-1", Boolean.TRUE.equals(gate.get("executionAllowed")));

        assertEquals(false, request.get("kycRequestReady"));
        assertEquals("SCREENING_POLICY_UNAVAILABLE", request.get("screeningReasonCode"));
        assertEquals(TransferStatus.COMPLIANCE_HOLD, fixture.instruction.getStatus());
        assertEquals("HOLD", aggregate.get("complianceOutcome"));
        assertEquals(false, gate.get("executionAllowed"));
        assertEquals("UNKNOWN", submission.get("submissionOutcome"));
        verify(publisher, never()).publish(any());
        verify(fixture.submissionPort, never()).submit(any());
    }

    @Test
    void timeoutIsPersistedBeforeJoinAndCannotReachSubmission() {
        Fixture fixture = new Fixture(TransferStatus.SCREENING_PENDING);
        ScreeningRequestRecord kycRequest = request(ScreeningType.KYC, ScreeningRequestStatus.PUBLISHED);
        when(fixture.requests.findByRequestId("transfer-1:KYC")).thenReturn(Optional.of(kycRequest));
        when(fixture.requests.save(any(ScreeningRequestRecord.class)))
            .thenAnswer(call -> call.getArgument(0));
        for (ScreeningType type : new ScreeningType[] { ScreeningType.AML, ScreeningType.SANCTIONS }) {
            when(fixture.requests.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                .thenReturn(Optional.of(request(type, ScreeningRequestStatus.PUBLISHED)));
        }
        when(fixture.requests.findByInstructionIdAndScreeningType("transfer-1", "KYC"))
            .thenReturn(Optional.of(kycRequest));
        ScreeningRequestService requestService = new ScreeningRequestService(
            fixture.requests,
            (instruction, type) -> Optional.empty(),
            request -> { }
        );
        fixture.screeningRequests = requestService;

        TransferLifecycleJobHandlers handlers = fixture.handlers();
        Map<String, Object> timeout = handlers.recordScreeningTimeout("transfer-1", ScreeningType.KYC);
        Map<String, Object> aggregate = handlers.aggregateCompliance("transfer-1");
        Map<String, Object> gate = handlers.preExecutionGate(
            "transfer-1", (String) aggregate.get("complianceOutcome")
        );
        handlers.submit("transfer-1", Boolean.TRUE.equals(gate.get("executionAllowed")));

        assertEquals(true, timeout.get("screeningHoldPersisted"));
        assertEquals(ScreeningRequestStatus.TIMED_OUT.name(), kycRequest.getPublicationStatus());
        assertEquals(TransferStatus.COMPLIANCE_HOLD, fixture.instruction.getStatus());
        assertEquals("HOLD", aggregate.get("complianceOutcome"));
        assertEquals(false, gate.get("executionAllowed"));
        verify(fixture.submissionPort, never()).submit(any());
    }

    @Test
    void everyScreeningNonApprovalIsBlockedBeforeSubmission() {
        for (ScreeningType affectedType : ScreeningType.values()) {
            for (ScreeningDecision nonApproval : new ScreeningDecision[] {
                ScreeningDecision.REJECTED,
                ScreeningDecision.REVIEW_REQUIRED,
                ScreeningDecision.ERROR,
                ScreeningDecision.RESTRICTED
            }) {
                Fixture fixture = new Fixture(TransferStatus.SCREENING_PENDING);
                for (ScreeningType type : ScreeningType.values()) {
                    ScreeningDecision decision = type == affectedType
                        ? nonApproval
                        : ScreeningDecision.APPROVED;
                    when(fixture.results.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                        .thenReturn(Optional.of(result(type, decision)));
                    when(fixture.requests.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                        .thenReturn(Optional.of(request(type, ScreeningRequestStatus.PUBLISHED)));
                }

                TransferLifecycleJobHandlers handlers = fixture.handlers();
                Map<String, Object> aggregate = handlers.aggregateCompliance("transfer-1");
                Map<String, Object> gate = handlers.preExecutionGate(
                    "transfer-1", (String) aggregate.get("complianceOutcome")
                );
                handlers.submit("transfer-1", Boolean.TRUE.equals(gate.get("executionAllowed")));

                assertFalse(Boolean.TRUE.equals(gate.get("executionAllowed")),
                    affectedType.name() + "-" + nonApproval.name());
                verify(fixture.submissionPort, never()).submit(any());
            }
        }
    }

    @Test
    void allThreeApprovalsStillRequireConfiguredPreExecutionPolicy() {
        Fixture fixture = new Fixture(TransferStatus.COMPLIANCE_APPROVED);
        for (ScreeningType type : ScreeningType.values()) {
            when(fixture.results.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                .thenReturn(Optional.of(result(type, ScreeningDecision.APPROVED)));
            when(fixture.requests.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                .thenReturn(Optional.of(request(type, ScreeningRequestStatus.PUBLISHED)));
        }
        fixture.preExecutionPolicy = instruction -> false;

        Map<String, Object> gate = fixture.handlers().preExecutionGate("transfer-1", "APPROVED");
        fixture.handlers().submit("transfer-1", Boolean.TRUE.equals(gate.get("executionAllowed")));

        assertEquals(false, gate.get("executionAllowed"));
        assertEquals(TransferStatus.COMPLIANCE_HOLD, fixture.instruction.getStatus());
        verify(fixture.submissionPort, never()).submit(any());
    }

    @Test
    void interruptedSubmissionReconcilesThePreviouslyJournaledHashWithoutResubmitting() {
        Fixture fixture = new Fixture(TransferStatus.SUBMISSION_IN_PROGRESS);
        String hash = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        when(fixture.submissionPort.submit(any())).thenReturn(new TransferSubmission(
            "transfer-1",
            1337,
            fixture.instruction.getTokenIdentifier(),
            fixture.instruction.getSourceWallet(),
            fixture.instruction.getDestinationWallet(),
            fixture.instruction.getAmount(),
            hash,
            TransferSubmission.Outcome.SUBMITTED,
            null
        ));

        Map<String, Object> result = fixture.handlers().submit("transfer-1", true);

        assertEquals("SUBMITTED", result.get("submissionOutcome"));
        assertEquals(hash, result.get("transactionHash"));
        assertEquals(hash, fixture.instruction.getTransactionHash());
        assertEquals(TransferStatus.TRANSACTION_SUBMITTED, fixture.instruction.getStatus());
        verify(fixture.submissionPort).submit(any());
    }

    @Test
    void uncertainSubmissionPreservesKnownHashForReconciliation() {
        Fixture fixture = new Fixture(TransferStatus.READY_FOR_EXECUTION);
        String hash = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        when(fixture.submissionPort.submit(any())).thenReturn(new TransferSubmission(
            "transfer-1",
            1337,
            fixture.instruction.getTokenIdentifier(),
            fixture.instruction.getSourceWallet(),
            fixture.instruction.getDestinationWallet(),
            fixture.instruction.getAmount(),
            hash,
            TransferSubmission.Outcome.UNKNOWN,
            "SUBMISSION_JOURNAL_UNAVAILABLE"
        ));

        Map<String, Object> submission = fixture.handlers().submit("transfer-1", true);

        assertEquals("UNKNOWN", submission.get("submissionOutcome"));
        assertEquals(hash, submission.get("transactionHash"));
        assertEquals(hash, fixture.instruction.getTransactionHash());
        assertEquals(TransferStatus.TRANSACTION_UNKNOWN, fixture.instruction.getStatus());
    }

    @Test
    void pendingTransactionRemainsCorrelatedUntilFinalityCanBeReconciled() {
        Fixture fixture = new Fixture(TransferStatus.TRANSACTION_SUBMITTED);
        String hash = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        fixture.instruction.setTransactionHash(hash);
        fixture.reconciliationPort = (instruction, transactionHash) ->
            new TransferReconciliationPort.ReconciliationDecision(true, null);
        TransferLifecycleJobHandlers handlers = fixture.handlers();

        Map<String, Object> pending = handlers.reconcile("transfer-1", hash, "PENDING", false);
        assertEquals(true, pending.get("reconciliationPending"));
        assertEquals(false, pending.get("reconciliationMatched"));
        assertEquals(TransferStatus.TRANSACTION_PENDING, fixture.instruction.getStatus());

        Map<String, Object> confirmed = handlers.reconcile("transfer-1", hash, "CONFIRMED", true);

        assertEquals(true, confirmed.get("reconciliationMatched"));
        assertEquals(TransferStatus.COMPLETED, fixture.instruction.getStatus());
    }

    private ScreeningRequestRecord request(ScreeningType type, ScreeningRequestStatus status) {
        return new ScreeningRequestRecord(
            "transfer-1:" + type.name(),
            "transfer-1:" + type.name(),
            "transfer-1",
            "configured-provider",
            type.name(),
            "approved-version",
            Instant.parse("2026-10-11T00:00:00Z"),
            status,
            null
        );
    }

    private ScreeningResultRecord result(ScreeningType type, ScreeningDecision decision) {
        return new ScreeningResultRecord(
            type.name() + "-message",
            "configured-provider",
            type.name() + "-message",
            "transfer-1",
            "transfer-1:" + type.name(),
            "transfer-1:" + type.name(),
            type.name(),
            decision.name(),
            Instant.parse("2026-10-11T00:00:00Z"),
            Instant.parse("2026-10-11T00:00:01Z")
        );
    }

    private static final class Fixture {
        private final TransferInstructionRepository instructions = mock(TransferInstructionRepository.class);
        private final ScreeningRequestRepository requests = mock(ScreeningRequestRepository.class);
        private final ScreeningResultRepository results = mock(ScreeningResultRepository.class);
        private final TransferLifecycleService lifecycle = mock(TransferLifecycleService.class);
        private final TransferSubmissionPort submissionPort = mock(TransferSubmissionPort.class);
        private final TransferInstruction instruction = new TransferInstruction();
        private ScreeningRequestService screeningRequests = mock(ScreeningRequestService.class);
        private TransferValidationPolicy validationPolicy = current -> new TransferValidationPolicy.ValidationDecision(
            true, null
        );
        private PreExecutionPolicy preExecutionPolicy = current -> true;
        private TransferReconciliationPort reconciliationPort = (transfer, hash) ->
            new TransferReconciliationPort.ReconciliationDecision(false, "AUTHORITATIVE_CHAIN_DATA_UNAVAILABLE");

        private Fixture(TransferStatus status) {
            instruction.setInstructionId("transfer-1");
            instruction.setSourceWallet("0x1111111111111111111111111111111111111111");
            instruction.setDestinationWallet("0x2222222222222222222222222222222222222222");
            instruction.setTokenIdentifier("0x3333333333333333333333333333333333333333");
            instruction.setAmount("5");
            instruction.setNetworkId(1337);
            instruction.setStatus(status);
            when(instructions.findById("transfer-1")).thenAnswer(call -> Optional.of(instruction));
            when(lifecycle.transition(
                anyString(), any(TransferStatus.class), any(TransferStatus.class),
                nullable(String.class), nullable(String.class)
            )).thenAnswer(call -> {
                instruction.setStatus(call.getArgument(2));
                instruction.setReasonCode(call.getArgument(3));
                instruction.setTransactionHash(call.getArgument(4));
                return instruction;
            });
        }

        private TransferLifecycleJobHandlers handlers() {
            return new TransferLifecycleJobHandlers(
                instructions,
                requests,
                results,
                lifecycle,
                validationPolicy,
                screeningRequests,
                new ScreeningDecisionAggregator(),
                preExecutionPolicy,
                submissionPort,
                reconciliationPort
            );
        }
    }
}
