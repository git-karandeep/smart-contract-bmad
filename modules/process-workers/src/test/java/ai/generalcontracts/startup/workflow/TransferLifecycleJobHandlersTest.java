package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TransferLifecycleJobHandlersTest {
    @Test
    void missingValidationPolicyPersistsFailureAndCannotReachSubmission() {
        Fixture fixture = new Fixture(TransferStatus.RECEIVED);
        fixture.validationPolicy = TransferValidationPolicy.withEligibilityPolicy(null);
        TransferLifecycleJobHandlers handlers = fixture.handlers();

        Map<String, Object> validation = handlers.validate("transfer-1");
        Map<String, Object> screening = handlers.requestScreening("transfer-1", ScreeningType.KYC);
        Map<String, Object> submission = handlers.submit("transfer-1", true);

        assertEquals(false, validation.get("validationPassed"));
        assertEquals("TRANSFER_ELIGIBILITY_POLICY_UNAVAILABLE", validation.get("validationReasonCode"));
        assertEquals(TransferStatus.VALIDATION_FAILED, fixture.instruction.getStatus());
        assertEquals("TRANSFER_ELIGIBILITY_POLICY_UNAVAILABLE", fixture.instruction.getReasonCode());
        assertEquals(false, screening.get("kycRequestReady"));
        assertEquals("UNKNOWN", submission.get("submissionOutcome"));
        verify(fixture.screeningRequests, never()).createAndPublish(any(), any());
        verify(fixture.submissionPort, never()).submit(any());
    }

    @Test
    void configuredEligibilityPolicyAllowsStructurallyValidInstructionToScreening() {
        Fixture fixture = new Fixture(TransferStatus.RECEIVED);
        fixture.validationPolicy = TransferValidationPolicy.withEligibilityPolicy(instruction ->
            new TransferValidationPolicy.ValidationDecision(true, null)
        );

        Map<String, Object> validation = fixture.handlers().validate("transfer-1");

        assertEquals(true, validation.get("validationPassed"));
        assertEquals(TransferStatus.SCREENING_PENDING, fixture.instruction.getStatus());
        assertEquals(null, fixture.instruction.getReasonCode());
    }

    @Test
    void structuralInstructionFailuresPersistStableReasonsAndCannotReachScreeningOrSubmission() {
        List<Consumer<TransferInstruction>> invalidInstructions = List.of(
            instruction -> instruction.setSourceWallet("not-an-address"),
            instruction -> instruction.setDestinationWallet("0x1234"),
            instruction -> instruction.setDestinationWallet(instruction.getSourceWallet().toUpperCase()),
            instruction -> instruction.setTokenIdentifier("0x1234"),
            instruction -> instruction.setAmount("0"),
            instruction -> instruction.setAmount("1e3"),
            instruction -> instruction.setNetworkId(0)
        );
        List<String> reasons = List.of(
            "TRANSFER_SOURCE_WALLET_INVALID",
            "TRANSFER_DESTINATION_WALLET_INVALID",
            "TRANSFER_SOURCE_DESTINATION_IDENTICAL",
            "TRANSFER_TOKEN_IDENTIFIER_INVALID",
            "TRANSFER_AMOUNT_INVALID",
            "TRANSFER_AMOUNT_INVALID",
            "TRANSFER_NETWORK_ID_INVALID"
        );

        for (int index = 0; index < invalidInstructions.size(); index++) {
            Fixture fixture = new Fixture(TransferStatus.RECEIVED);
            invalidInstructions.get(index).accept(fixture.instruction);
            fixture.validationPolicy = TransferValidationPolicy.withEligibilityPolicy(instruction ->
                new TransferValidationPolicy.ValidationDecision(true, null)
            );
            TransferLifecycleJobHandlers handlers = fixture.handlers();

            Map<String, Object> validation = handlers.validate("transfer-1");
            Map<String, Object> screening = handlers.requestScreening("transfer-1", ScreeningType.KYC);
            Map<String, Object> submission = handlers.submit("transfer-1", true);

            assertEquals(false, validation.get("validationPassed"), reasons.get(index));
            assertEquals(reasons.get(index), validation.get("validationReasonCode"));
            assertEquals(TransferStatus.VALIDATION_FAILED, fixture.instruction.getStatus());
            assertEquals(reasons.get(index), fixture.instruction.getReasonCode());
            assertEquals(false, screening.get("kycRequestReady"));
            assertEquals("UNKNOWN", submission.get("submissionOutcome"));
            verify(fixture.screeningRequests, never()).createAndPublish(any(), any());
            verify(fixture.submissionPort, never()).submit(any());
        }
    }

    @Test
    void eligibilityRejectionAndIntegrationFailureFailClosedWithStableReasons() {
        TransferEligibilityPolicy[] policies = new TransferEligibilityPolicy[] {
            instruction -> TransferValidationPolicy.ValidationDecision.rejected("TOKEN_NOT_ELIGIBLE"),
            instruction -> {
                throw new IllegalStateException("policy unavailable");
            }
        };
        String[] reasons = { "TOKEN_NOT_ELIGIBLE", "TRANSFER_ELIGIBILITY_POLICY_FAILED" };

        for (int index = 0; index < policies.length; index++) {
            Fixture fixture = new Fixture(TransferStatus.RECEIVED);
            fixture.validationPolicy = TransferValidationPolicy.withEligibilityPolicy(policies[index]);
            TransferLifecycleJobHandlers handlers = fixture.handlers();

            Map<String, Object> validation = handlers.validate("transfer-1");
            Map<String, Object> screening = handlers.requestScreening("transfer-1", ScreeningType.KYC);
            handlers.submit("transfer-1", true);

            assertEquals(false, validation.get("validationPassed"));
            assertEquals(reasons[index], validation.get("validationReasonCode"));
            assertEquals(TransferStatus.VALIDATION_FAILED, fixture.instruction.getStatus());
            assertEquals(reasons[index], fixture.instruction.getReasonCode());
            assertEquals(false, screening.get("kycRequestReady"));
            verify(fixture.screeningRequests, never()).createAndPublish(any(), any());
            verify(fixture.submissionPort, never()).submit(any());
        }
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
        when(fixture.requests.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
            .thenReturn(List.of(kycRequest));
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
        org.junit.jupiter.api.Assertions.assertTrue(fixture.instruction.getComplianceEvaluation().contains("outcome=HOLD"));
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
                    if (type == ScreeningType.KYC) {
                        when(fixture.results.findAllByInstructionIdAndScreeningType("transfer-1", type.name()))
                            .thenReturn(List.of(requestResult(
                                "transfer-1:KYC", type, decision
                            )));
                        when(fixture.requests.findAllByInstructionIdAndScreeningType("transfer-1", type.name()))
                            .thenReturn(List.of(request(type, ScreeningRequestStatus.PUBLISHED)));
                    } else {
                        when(fixture.results.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                            .thenReturn(Optional.of(result(type, decision)));
                        when(fixture.requests.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                            .thenReturn(Optional.of(request(type, ScreeningRequestStatus.PUBLISHED)));
                    }
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
    void gateRecordsStableReasonAndHoldsWhenInstructionChangedAfterApproval() {
        Fixture fixture = approvedFixture();
        TransferLifecycleJobHandlers handlers = fixture.handlers();
        Map<String, Object> aggregate = handlers.aggregateCompliance("transfer-1");
        fixture.instruction.setAmount("500");

        Map<String, Object> gate = handlers.preExecutionGate("transfer-1", (String) aggregate.get("complianceOutcome"));

        assertEquals(false, gate.get("executionAllowed"));
        assertEquals("PRE_EXECUTION_INSTRUCTION_CHANGED", gate.get("executionGateReasonCode"));
        assertEquals("transfer-1", gate.get("executionGateInstructionId"));
        assertEquals(TransferStatus.COMPLIANCE_HOLD, fixture.instruction.getStatus());
    }

    @Test
    void gateBlocksRevokedEligibilityCancellationAndMissingApproval() {
        Fixture revoked = approvedFixture();
        TransferLifecycleJobHandlers handlers = revoked.handlers();
        Map<String, Object> aggregate = handlers.aggregateCompliance("transfer-1");
        revoked.validationPolicy = current -> TransferValidationPolicy.ValidationDecision.rejected("REVOKED");
        Map<String, Object> gate = revoked.handlers().preExecutionGate("transfer-1", (String) aggregate.get("complianceOutcome"));
        assertEquals("PRE_EXECUTION_VALIDATION_FAILED", gate.get("executionGateReasonCode"));

        Fixture cancelled = new Fixture(TransferStatus.CANCELLED);
        assertEquals(
            "PRE_EXECUTION_CANCELLED",
            cancelled.handlers().preExecutionGate("transfer-1", "APPROVED").get("executionGateReasonCode")
        );

        Fixture stale = new Fixture(TransferStatus.COMPLIANCE_APPROVED);
        assertEquals(
            "PRE_EXECUTION_COMPLIANCE_NOT_APPROVED",
            stale.handlers().preExecutionGate("transfer-1", "WAITING").get("executionGateReasonCode")
        );

        Fixture missing = new Fixture(TransferStatus.COMPLIANCE_APPROVED);
        assertEquals(
            "PRE_EXECUTION_APPROVAL_MISSING_OR_STALE",
            missing.handlers().preExecutionGate("transfer-1", "APPROVED").get("executionGateReasonCode")
        );
        verify(cancelled.submissionPort, never()).submit(any());
    }

    private Fixture approvedFixture() {
        Fixture fixture = new Fixture(TransferStatus.SCREENING_PENDING);
        for (ScreeningType type : ScreeningType.values()) {
            when(fixture.requests.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                .thenReturn(Optional.of(request(type, ScreeningRequestStatus.PUBLISHED)));
            when(fixture.results.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                .thenReturn(Optional.of(result(type, ScreeningDecision.APPROVED)));
        }
        when(fixture.requests.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
            .thenReturn(List.of(kycRequest("kyc-request-a", "subject-a", 1)));
        when(fixture.results.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
            .thenReturn(List.of(requestResult("kyc-request-a", ScreeningType.KYC, ScreeningDecision.APPROVED)));
        return fixture;
    }

    @Test
    void correlatesAndRequiresApprovalFromEveryKycSubjectBeforeExecution() {
        Fixture fixture = new Fixture(TransferStatus.SCREENING_PENDING);
        ScreeningRequestRecord firstRequest = kycRequest("kyc-request-a", "subject-a", 2);
        ScreeningRequestRecord secondRequest = kycRequest("kyc-request-b", "subject-b", 2);
        ScreeningResultRecord firstResult = requestResult(
            "kyc-request-a", ScreeningType.KYC, ScreeningDecision.APPROVED
        );
        ScreeningResultRecord secondResult = requestResult(
            "kyc-request-b", ScreeningType.KYC, ScreeningDecision.APPROVED
        );
        when(fixture.requests.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
            .thenReturn(List.of(firstRequest, secondRequest));
        when(fixture.results.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
            .thenReturn(List.of(secondResult, firstResult));
        for (ScreeningType type : new ScreeningType[] { ScreeningType.AML, ScreeningType.SANCTIONS }) {
            when(fixture.requests.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                .thenReturn(Optional.of(request(type, ScreeningRequestStatus.PUBLISHED)));
            when(fixture.results.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                .thenReturn(Optional.of(result(type, ScreeningDecision.APPROVED)));
        }

        TransferLifecycleJobHandlers handlers = fixture.handlers();
        Map<String, Object> aggregate = handlers.aggregateCompliance("transfer-1");
        Map<String, Object> gate = handlers.preExecutionGate(
            "transfer-1", (String) aggregate.get("complianceOutcome")
        );

        assertEquals("APPROVED", aggregate.get("complianceOutcome"));
        assertEquals(true, gate.get("executionAllowed"));
        verify(fixture.lifecycle).transition(
            "transfer-1",
            TransferStatus.COMPLIANCE_APPROVED,
            TransferStatus.READY_FOR_EXECUTION,
            null,
            null
        );
    }

    @Test
    void rejectsAnyNonApprovalFromAnyRequiredKycSubject() {
        Map<ScreeningDecision, String> outcomes = Map.of(
            ScreeningDecision.REJECTED, "REJECTED",
            ScreeningDecision.REVIEW_REQUIRED, "MANUAL_REVIEW",
            ScreeningDecision.ERROR, "HOLD"
        );
        outcomes.forEach((decision, expectedOutcome) -> {
            Fixture fixture = new Fixture(TransferStatus.SCREENING_PENDING);
            when(fixture.requests.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
                .thenReturn(List.of(
                    kycRequest("kyc-request-a", "subject-a", 2),
                    kycRequest("kyc-request-b", "subject-b", 2)
                ));
            when(fixture.results.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
                .thenReturn(List.of(
                    requestResult("kyc-request-a", ScreeningType.KYC, ScreeningDecision.APPROVED),
                    requestResult("kyc-request-b", ScreeningType.KYC, decision)
                ));
            for (ScreeningType type : new ScreeningType[] { ScreeningType.AML, ScreeningType.SANCTIONS }) {
                when(fixture.requests.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                    .thenReturn(Optional.of(request(type, ScreeningRequestStatus.PUBLISHED)));
                when(fixture.results.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                    .thenReturn(Optional.of(result(type, ScreeningDecision.APPROVED)));
            }

            Map<String, Object> aggregate = fixture.handlers().aggregateCompliance("transfer-1");

            assertEquals(expectedOutcome, aggregate.get("complianceOutcome"));
            assertFalse(Boolean.TRUE.equals(aggregate.get("complianceApproved")));
        });
    }

    @Test
    void explicitPersistedKycNotRequiredDoesNotMeanPolicyUnavailable() {
        Fixture fixture = new Fixture(TransferStatus.SCREENING_PENDING);
        ScreeningRequestRecord kycNotRequired = new ScreeningRequestRecord(
            "transfer-1:KYC",
            "transfer-1:KYC",
            "transfer-1",
            null,
            "KYC",
            null,
            Instant.parse("2026-10-11T00:00:00Z"),
            ScreeningRequestStatus.NOT_REQUIRED,
            null
        );
        when(fixture.requests.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
            .thenReturn(List.of(kycNotRequired));
        for (ScreeningType type : new ScreeningType[] { ScreeningType.AML, ScreeningType.SANCTIONS }) {
            when(fixture.requests.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                .thenReturn(Optional.of(request(type, ScreeningRequestStatus.PUBLISHED)));
            when(fixture.results.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                .thenReturn(Optional.of(result(type, ScreeningDecision.APPROVED)));
        }

        TransferLifecycleJobHandlers handlers = fixture.handlers();
        Map<String, Object> aggregate = handlers.aggregateCompliance("transfer-1");
        Map<String, Object> gate = handlers.preExecutionGate(
            "transfer-1", (String) aggregate.get("complianceOutcome")
        );

        assertEquals("APPROVED", aggregate.get("complianceOutcome"));
        assertEquals(true, gate.get("executionAllowed"));
    }

    @Test
    void allThreeApprovalsStillRequireConfiguredPreExecutionPolicy() {
        Fixture fixture = new Fixture(TransferStatus.COMPLIANCE_APPROVED);
        for (ScreeningType type : ScreeningType.values()) {
            if (type == ScreeningType.KYC) {
                when(fixture.results.findAllByInstructionIdAndScreeningType("transfer-1", type.name()))
                    .thenReturn(List.of(requestResult(
                        "transfer-1:KYC", type, ScreeningDecision.APPROVED
                    )));
                when(fixture.requests.findAllByInstructionIdAndScreeningType("transfer-1", type.name()))
                    .thenReturn(List.of(request(type, ScreeningRequestStatus.PUBLISHED)));
            } else {
                when(fixture.results.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                    .thenReturn(Optional.of(result(type, ScreeningDecision.APPROVED)));
                when(fixture.requests.findByInstructionIdAndScreeningType("transfer-1", type.name()))
                    .thenReturn(Optional.of(request(type, ScreeningRequestStatus.PUBLISHED)));
            }
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
    void cancellationTransitionsAReadyTransferToCancelled() {
        Fixture fixture = new Fixture(TransferStatus.READY_FOR_EXECUTION);

        Map<String, Object> cancellation = fixture.handlers().cancel("transfer-1");

        assertEquals("CANCELLED", cancellation.get("cancellationOutcome"));
        assertEquals(TransferStatus.CANCELLED, fixture.instruction.getStatus());
        verify(fixture.lifecycle).transition(
            "transfer-1",
            TransferStatus.READY_FOR_EXECUTION,
            TransferStatus.CANCELLED,
            "TRANSFER_CANCELLED",
            null
        );
    }

    @Test
    void cancellationAfterSubmissionStartsLeavesTransactionMonitoringUndisturbed() {
        Fixture fixture = new Fixture(TransferStatus.SUBMISSION_IN_PROGRESS);

        Map<String, Object> cancellation = fixture.handlers().cancel("transfer-1");

        assertEquals("NOT_CANCELLED", cancellation.get("cancellationOutcome"));
        assertEquals("SUBMISSION_ALREADY_STARTED", cancellation.get("cancellationReasonCode"));
        assertEquals(TransferStatus.SUBMISSION_IN_PROGRESS, fixture.instruction.getStatus());
        verify(fixture.lifecycle, never()).transition(
            anyString(),
            any(TransferStatus.class),
            any(TransferStatus.class),
            nullable(String.class),
            nullable(String.class)
        );
    }

    @Test
    void cancellationLosesTheAtomicRaceWhenSubmissionClaimsTheInstructionFirst() {
        Fixture fixture = new Fixture(TransferStatus.READY_FOR_EXECUTION);
        doAnswer(invocation -> {
            fixture.instruction.setStatus(TransferStatus.SUBMISSION_IN_PROGRESS);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer state changed");
        }).when(fixture.lifecycle).transition(
            "transfer-1",
            TransferStatus.READY_FOR_EXECUTION,
            TransferStatus.CANCELLED,
            "TRANSFER_CANCELLED",
            null
        );

        Map<String, Object> cancellation = fixture.handlers().cancel("transfer-1");

        assertEquals("NOT_CANCELLED", cancellation.get("cancellationOutcome"));
        assertEquals("SUBMISSION_ALREADY_STARTED", cancellation.get("cancellationReasonCode"));
        assertEquals(TransferStatus.SUBMISSION_IN_PROGRESS, fixture.instruction.getStatus());
    }

    @Test
    void cancellationRetriesAConcurrentUpdateWhileTheTransferRemainsPreSubmission() {
        Fixture fixture = new Fixture(TransferStatus.READY_FOR_EXECUTION);
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent state update");
            }
            fixture.instruction.setStatus(TransferStatus.CANCELLED);
            return fixture.instruction;
        }).when(fixture.lifecycle).transition(
            "transfer-1",
            TransferStatus.READY_FOR_EXECUTION,
            TransferStatus.CANCELLED,
            "TRANSFER_CANCELLED",
            null
        );

        Map<String, Object> cancellation = fixture.handlers().cancel("transfer-1");

        assertEquals("CANCELLED", cancellation.get("cancellationOutcome"));
        assertEquals(TransferStatus.CANCELLED, fixture.instruction.getStatus());
        verify(fixture.lifecycle, times(2)).transition(
            "transfer-1",
            TransferStatus.READY_FOR_EXECUTION,
            TransferStatus.CANCELLED,
            "TRANSFER_CANCELLED",
            null
        );
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

    @Test
    void revertedReplacedAndUnknownOutcomesNeverCompleteOrResubmit() {
        String hash = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        for (Object[] row : new Object[][] {
            {"REVERTED", true, TransferStatus.TRANSACTION_REVERTED},
            {"REPLACED", false, TransferStatus.TRANSACTION_UNKNOWN},
            {"UNKNOWN", false, TransferStatus.TRANSACTION_UNKNOWN}
        }) {
            Fixture fixture = new Fixture(TransferStatus.TRANSACTION_SUBMITTED);
            fixture.instruction.setTransactionHash(hash);
            Map<String, Object> result = fixture.handlers().reconcile(
                "transfer-1", hash, (String) row[0], (Boolean) row[1]
            );

            assertEquals(false, result.get("reconciliationMatched"));
            assertEquals(row[2], fixture.instruction.getStatus());
            assertEquals(hash, fixture.instruction.getTransactionHash());
            verify(fixture.submissionPort, never()).submit(any());
        }
    }

    @Test
    void replayedReconciliationDoesNotRepeatTransitionsAndMismatchRaisesInvestigation() {
        String hash = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        Fixture ok = new Fixture(TransferStatus.TRANSACTION_PENDING);
        ok.instruction.setTransactionHash(hash);
        ok.reconciliationPort = (instruction, transactionHash) ->
            new TransferReconciliationPort.ReconciliationDecision(true, null);
        TransferLifecycleJobHandlers handlers = ok.handlers();
        assertEquals(true, handlers.reconcile("transfer-1", hash, "CONFIRMED", true).get("reconciliationMatched"));
        org.mockito.Mockito.clearInvocations(ok.lifecycle);

        Map<String, Object> replay = handlers.reconcile("transfer-1", hash, "CONFIRMED", true);

        assertEquals(true, replay.get("reconciliationMatched"));
        assertEquals(true, replay.get("reconciliationReplay"));
        org.mockito.Mockito.verifyNoInteractions(ok.lifecycle);

        Fixture bad = new Fixture(TransferStatus.TRANSACTION_PENDING);
        bad.instruction.setTransactionHash(hash);
        bad.reconciliationPort = (instruction, transactionHash) ->
            new TransferReconciliationPort.ReconciliationDecision(false, "AMOUNT_MISMATCH");
        TransferLifecycleJobHandlers badHandlers = bad.handlers();
        Map<String, Object> mismatch = badHandlers.reconcile("transfer-1", hash, "CONFIRMED", true);
        assertEquals(false, mismatch.get("reconciliationMatched"));
        assertEquals("AMOUNT_MISMATCH", mismatch.get("reconciliationReasonCode"));
        assertEquals(true, mismatch.get("investigationRequired"));
        assertEquals(TransferStatus.RECONCILIATION_MISMATCH, bad.instruction.getStatus());
        Map<String, Object> mismatchReplay = badHandlers.reconcile("transfer-1", hash, "CONFIRMED", true);
        assertEquals("AMOUNT_MISMATCH", mismatchReplay.get("reconciliationReasonCode"));
        assertEquals(true, mismatchReplay.get("investigationRequired"));
    }

    @Test
    void workerRetryAfterUncertainSubmissionNeverSubmitsASecondTransaction() {
        Fixture fixture = new Fixture(TransferStatus.TRANSACTION_UNKNOWN);
        fixture.instruction.setTransactionHash("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        TransferLifecycleJobHandlers handlers = fixture.handlers();

        for (int attempt = 0; attempt < 3; attempt++) {
            assertEquals("UNKNOWN", handlers.submit("transfer-1", true).get("submissionOutcome"));
        }

        verify(fixture.submissionPort, never()).submit(any());
        Fixture submitted = new Fixture(TransferStatus.TRANSACTION_SUBMITTED);
        submitted.instruction.setTransactionHash("0xbbbb");
        assertEquals("SUBMITTED", submitted.handlers().submit("transfer-1", true).get("submissionOutcome"));
        assertEquals("SUBMITTED", submitted.handlers().submit("transfer-1", true).get("submissionOutcome"));
        verify(submitted.submissionPort, never()).submit(any());
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

    private ScreeningRequestRecord kycRequest(String requestId, String subjectReference, int requiredCount) {
        ScreeningRequestRecord request = new ScreeningRequestRecord(
            requestId,
            requestId + ":correlation",
            "transfer-1",
            "configured-provider",
            ScreeningType.KYC.name(),
            "provider-v2",
            Instant.parse("2026-10-11T00:00:00Z"),
            subjectReference,
            Map.of("caseRef", "opaque-" + subjectReference),
            "policy-v4",
            requiredCount
        );
        request.markPublished(Instant.parse("2026-10-11T00:00:01Z"));
        return request;
    }

    private ScreeningResultRecord requestResult(
        String requestId,
        ScreeningType type,
        ScreeningDecision decision
    ) {
        return new ScreeningResultRecord(
            requestId + "-result",
            "configured-provider",
            requestId + "-message",
            "transfer-1",
            requestId,
            requestId + ":correlation",
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
