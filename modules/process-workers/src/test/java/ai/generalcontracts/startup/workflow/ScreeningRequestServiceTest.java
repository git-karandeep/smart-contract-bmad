package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.generalcontracts.startup.transfer.ScreeningRequestRecord;
import ai.generalcontracts.startup.transfer.ScreeningRequestRepository;
import ai.generalcontracts.startup.transfer.ScreeningRequestStatus;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ScreeningRequestServiceTest {
    @Test
    void nonKycRequestJsonKeepsThePreviouslyMinimalShape() throws Exception {
        ScreeningRequestMessage request = new ScreeningRequestMessage(
            "transfer-1",
            "transfer-1:AML",
            "transfer-1:AML",
            "provider-1",
            ScreeningType.AML,
            "provider-v2",
            null
        );

        String payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request);

        assertFalse(payload.contains("subjectReference"));
        assertFalse(payload.contains("authorizedReferences"));
        assertFalse(payload.contains("policyVersion"));
    }

    @Test
    void createsAndPublishesOneMinimizedCorrelatedRequestPerPolicyRequiredParty() {
        ScreeningRequestRepository repository = mock(ScreeningRequestRepository.class);
        ScreeningRequestPublisher publisher = mock(ScreeningRequestPublisher.class);
        AtomicReference<List<ScreeningRequestRecord>> stored = new AtomicReference<>(new ArrayList<>());
        List<ScreeningRequestMessage> published = new ArrayList<>();
        when(repository.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
            .thenAnswer(call -> List.copyOf(stored.get()));
        when(repository.findByRequestId(any()))
            .thenAnswer(call -> stored.get().stream()
                .filter(record -> record.getRequestId().equals(call.getArgument(0)))
                .findFirst());
        when(repository.insert(any(ScreeningRequestRecord.class))).thenAnswer(call -> {
            ScreeningRequestRecord record = call.getArgument(0);
            stored.get().add(record);
            return record;
        });
        when(repository.save(any(ScreeningRequestRecord.class))).thenAnswer(call -> call.getArgument(0));
        org.mockito.Mockito.doAnswer(call -> {
            published.add(call.getArgument(0));
            return null;
        }).when(publisher).publish(any(ScreeningRequestMessage.class));
        ScreeningRequestPolicyResolver policy = new ScreeningRequestPolicyResolver() {
            @Override
            public Optional<ScreeningRequestPolicy> resolve(TransferInstruction instruction, ScreeningType type) {
                return Optional.empty();
            }

            @Override
            public Optional<KycScreeningPolicy> resolveKyc(TransferInstruction instruction) {
                return Optional.of(new KycScreeningPolicy(
                    KycScreeningPolicy.Requirement.REQUIRED,
                    "policy-v4",
                    "provider-7",
                    "provider-v2",
                    List.of(
                        new KycScreeningPolicy.Subject("subject-ref-a", Map.of("caseRef", "opaque-a")),
                        new KycScreeningPolicy.Subject("subject-ref-b", Map.of("caseRef", "opaque-b"))
                    )
                ));
            }
        };
        ScreeningRequestService service = new ScreeningRequestService(
            repository,
            policy,
            publisher,
            Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
        );

        ScreeningRequestService.RequestOutcome first = service.createKycRequests(instruction());
        ScreeningRequestService.RequestOutcome duplicate = service.createKycRequests(instruction());

        assertTrue(first.ready());
        assertEquals(2, first.requestIds().size());
        assertEquals(first.requestIds(), duplicate.requestIds());
        assertNotEquals(first.requestIds().get(0), first.requestIds().get(1));
        assertEquals(2, stored.get().size());
        assertEquals(2, published.size());
        assertNotEquals(published.get(0).correlationId(), published.get(1).correlationId());
        assertEquals("policy-v4", published.get(0).policyVersion());
        assertEquals("provider-v2", published.get(0).screeningVersion());
        assertEquals(1, published.get(0).authorizedReferences().size());
        assertTrue(List.of("opaque-a", "opaque-b").contains(
            published.get(0).authorizedReferences().get("caseRef")
        ));
        assertEquals(Instant.parse("2026-10-15T12:00:00Z"), published.get(0).requestedAt());
        assertEquals("policy-v4", stored.get().get(0).getPolicyVersion());
        assertEquals(2, stored.get().get(0).getRequiredSubjectCount());
        verify(repository, org.mockito.Mockito.times(2)).insert(any(ScreeningRequestRecord.class));
        verify(publisher, org.mockito.Mockito.times(2)).publish(any(ScreeningRequestMessage.class));
    }

    @Test
    void persistsExplicitNoKycRequiredSeparatelyAndDoesNotPublish() {
        ScreeningRequestRepository repository = mock(ScreeningRequestRepository.class);
        ScreeningRequestPublisher publisher = mock(ScreeningRequestPublisher.class);
        AtomicReference<ScreeningRequestRecord> stored = new AtomicReference<>();
        when(repository.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
            .thenAnswer(call -> stored.get() == null ? List.of() : List.of(stored.get()));
        when(repository.insert(any(ScreeningRequestRecord.class))).thenAnswer(call -> {
            stored.set(call.getArgument(0));
            return stored.get();
        });
        ScreeningRequestPolicyResolver policy = new ScreeningRequestPolicyResolver() {
            @Override
            public Optional<ScreeningRequestPolicy> resolve(TransferInstruction instruction, ScreeningType type) {
                return Optional.empty();
            }

            @Override
            public Optional<KycScreeningPolicy> resolveKyc(TransferInstruction instruction) {
                return Optional.of(KycScreeningPolicy.notRequired("policy-v4"));
            }
        };

        ScreeningRequestService.RequestOutcome outcome =
            new ScreeningRequestService(repository, policy, publisher).createKycRequests(instruction());

        assertTrue(outcome.ready());
        assertTrue(outcome.notRequired());
        assertTrue(outcome.requestIds().isEmpty());
        assertEquals(ScreeningRequestStatus.NOT_REQUIRED.name(), stored.get().getPublicationStatus());
        verify(publisher, never()).publish(any());
    }

    @Test
    void missingOrIncompleteKycPolicyIsDurableAndNeverPublishes() {
        for (Optional<KycScreeningPolicy> policyValue : List.<Optional<KycScreeningPolicy>>of(
            Optional.empty(),
            Optional.of(new KycScreeningPolicy(
                KycScreeningPolicy.Requirement.REQUIRED,
                "policy-v4",
                "provider-7",
                "provider-v2",
                List.of()
            ))
        )) {
            ScreeningRequestRepository repository = mock(ScreeningRequestRepository.class);
            ScreeningRequestPublisher publisher = mock(ScreeningRequestPublisher.class);
            AtomicReference<ScreeningRequestRecord> stored = new AtomicReference<>();
            when(repository.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
                .thenAnswer(call -> stored.get() == null ? List.of() : List.of(stored.get()));
            when(repository.insert(any(ScreeningRequestRecord.class))).thenAnswer(call -> {
                stored.set(call.getArgument(0));
                return stored.get();
            });
            ScreeningRequestPolicyResolver policy = new ScreeningRequestPolicyResolver() {
                @Override
                public Optional<ScreeningRequestPolicy> resolve(
                    TransferInstruction instruction,
                    ScreeningType type
                ) {
                    return Optional.empty();
                }

                @Override
                public Optional<KycScreeningPolicy> resolveKyc(TransferInstruction instruction) {
                    return policyValue;
                }
            };

            ScreeningRequestService.RequestOutcome outcome =
                new ScreeningRequestService(repository, policy, publisher).createKycRequests(instruction());

            assertFalse(outcome.ready());
            assertEquals(
                policyValue.isEmpty()
                    ? ScreeningRequestStatus.POLICY_UNAVAILABLE.name()
                    : ScreeningRequestStatus.INVALID_SUBJECT_DATA.name(),
                stored.get().getPublicationStatus()
            );
            verify(publisher, never()).publish(any());
        }
    }

    @Test
    void requiredKycPolicyWithoutProviderConfigurationFailsClosed() {
        ScreeningRequestRepository repository = mock(ScreeningRequestRepository.class);
        ScreeningRequestPublisher publisher = mock(ScreeningRequestPublisher.class);
        AtomicReference<ScreeningRequestRecord> stored = new AtomicReference<>();
        when(repository.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
            .thenAnswer(call -> stored.get() == null ? List.of() : List.of(stored.get()));
        when(repository.insert(any(ScreeningRequestRecord.class))).thenAnswer(call -> {
            stored.set(call.getArgument(0));
            return stored.get();
        });
        ScreeningRequestPolicyResolver policy = new ScreeningRequestPolicyResolver() {
            @Override
            public Optional<ScreeningRequestPolicy> resolve(TransferInstruction instruction, ScreeningType type) {
                return Optional.empty();
            }

            @Override
            public Optional<KycScreeningPolicy> resolveKyc(TransferInstruction instruction) {
                return Optional.of(KycScreeningPolicy.required(
                    "policy-v4",
                    null,
                    "provider-v2",
                    List.of(new KycScreeningPolicy.Subject("subject-ref", Map.of("caseRef", "opaque-ref")))
                ));
            }
        };

        ScreeningRequestService.RequestOutcome outcome =
            new ScreeningRequestService(repository, policy, publisher).createKycRequests(instruction());

        assertFalse(outcome.ready());
        assertEquals(ScreeningRequestStatus.POLICY_UNAVAILABLE.name(), stored.get().getPublicationStatus());
        verify(publisher, never()).publish(any());
    }

    @Test
    void providerPublicationFailureCannotLeaveKycSubjectsEligible() {
        ScreeningRequestRepository repository = mock(ScreeningRequestRepository.class);
        ScreeningRequestPublisher publisher = mock(ScreeningRequestPublisher.class);
        AtomicReference<List<ScreeningRequestRecord>> stored = new AtomicReference<>(new ArrayList<>());
        when(repository.findAllByInstructionIdAndScreeningType("transfer-1", "KYC"))
            .thenAnswer(call -> List.copyOf(stored.get()));
        when(repository.findByRequestId(any()))
            .thenAnswer(call -> stored.get().stream()
                .filter(record -> record.getRequestId().equals(call.getArgument(0)))
                .findFirst());
        when(repository.insert(any(ScreeningRequestRecord.class))).thenAnswer(call -> {
            ScreeningRequestRecord record = call.getArgument(0);
            stored.get().add(record);
            return record;
        });
        when(repository.save(any(ScreeningRequestRecord.class))).thenAnswer(call -> call.getArgument(0));
        doThrow(new IllegalStateException("provider broker unavailable"))
            .when(publisher).publish(any(ScreeningRequestMessage.class));
        ScreeningRequestPolicyResolver policy = new ScreeningRequestPolicyResolver() {
            @Override
            public Optional<ScreeningRequestPolicy> resolve(TransferInstruction instruction, ScreeningType type) {
                return Optional.empty();
            }

            @Override
            public Optional<KycScreeningPolicy> resolveKyc(TransferInstruction instruction) {
                return Optional.of(KycScreeningPolicy.required(
                    "policy-v4",
                    "provider-7",
                    "provider-v2",
                    List.of(
                        new KycScreeningPolicy.Subject("subject-ref-a", Map.of("caseRef", "opaque-a")),
                        new KycScreeningPolicy.Subject("subject-ref-b", Map.of("caseRef", "opaque-b"))
                    )
                ));
            }
        };
        ScreeningRequestService service = new ScreeningRequestService(repository, policy, publisher);

        ScreeningRequestService.RequestOutcome outcome = service.createKycRequests(instruction());
        service.recordTimeout("transfer-1", ScreeningType.KYC);

        assertFalse(outcome.ready());
        assertEquals(2, stored.get().size());
        assertTrue(stored.get().stream().allMatch(record ->
            !ScreeningRequestStatus.PENDING.name().equals(record.getPublicationStatus())
                && !ScreeningRequestStatus.PUBLISHED.name().equals(record.getPublicationStatus())
        ));
        verify(publisher).publish(any(ScreeningRequestMessage.class));
    }

    @Test
    void kycTimeoutUpdatesOnlyItsCorrelatedSubjectRequest() {
        ScreeningRequestRepository repository = mock(ScreeningRequestRepository.class);
        ScreeningRequestRecord first = request("request-a", "subject-a", ScreeningRequestStatus.PUBLISHED);
        ScreeningRequestRecord second = request("request-b", "subject-b", ScreeningRequestStatus.PUBLISHED);
        when(repository.findByRequestId("request-b")).thenReturn(Optional.of(second));
        when(repository.save(any(ScreeningRequestRecord.class))).thenAnswer(call -> call.getArgument(0));
        ScreeningRequestService service = new ScreeningRequestService(
            repository,
            (instruction, type) -> Optional.empty(),
            mock(ScreeningRequestPublisher.class)
        );

        service.recordTimeout("transfer-1", ScreeningType.KYC, "request-b");

        assertEquals(ScreeningRequestStatus.PUBLISHED.name(), first.getPublicationStatus());
        assertEquals(ScreeningRequestStatus.TIMED_OUT.name(), second.getPublicationStatus());
        verify(repository).save(second);
    }

    @Test
    void createsOnceAndPublishesAStableProviderNeutralRequest() {
        ScreeningRequestRepository repository = mock(ScreeningRequestRepository.class);
        ScreeningRequestPublisher publisher = mock(ScreeningRequestPublisher.class);
        AtomicReference<ScreeningRequestRecord> stored = new AtomicReference<>();
        when(repository.findByRequestId("transfer-1:AML"))
            .thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(repository.insert(any(ScreeningRequestRecord.class))).thenAnswer(call -> {
            ScreeningRequestRecord record = call.getArgument(0);
            stored.set(record);
            return record;
        });
        when(repository.save(any(ScreeningRequestRecord.class))).thenAnswer(call -> {
            ScreeningRequestRecord record = call.getArgument(0);
            stored.set(record);
            return record;
        });
        ScreeningRequestService service = new ScreeningRequestService(
            repository,
            (instruction, type) -> Optional.of(new ScreeningRequestPolicy("approved-provider", "policy-v7")),
            publisher
        );

        ScreeningRequestService.RequestOutcome first = service.createAndPublish(instruction(), ScreeningType.AML);
        ScreeningRequestService.RequestOutcome duplicate = service.createAndPublish(instruction(), ScreeningType.AML);

        assertTrue(first.ready());
        assertEquals(first.requestId(), duplicate.requestId());
        assertEquals("transfer-1:AML", first.requestId());
        assertEquals(ScreeningRequestStatus.PUBLISHED.name(), stored.get().getPublicationStatus());
        verify(repository).insert(any(ScreeningRequestRecord.class));
        verify(publisher).publish(any(ScreeningRequestMessage.class));
    }

    @Test
    void amlRequestCarriesOnlyConfiguredContextAndPolicyVersion() {
        ScreeningRequestRepository repository = mock(ScreeningRequestRepository.class);
        ScreeningRequestPublisher publisher = mock(ScreeningRequestPublisher.class);
        AtomicReference<ScreeningRequestRecord> stored = new AtomicReference<>();
        List<ScreeningRequestMessage> published = new ArrayList<>();
        when(repository.findByRequestId("transfer-1:AML")).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(repository.insert(any(ScreeningRequestRecord.class))).thenAnswer(call -> {
            stored.set(call.getArgument(0));
            return call.getArgument(0);
        });
        when(repository.save(any(ScreeningRequestRecord.class))).thenAnswer(call -> call.getArgument(0));
        org.mockito.Mockito.doAnswer(call -> {
            published.add(call.getArgument(0));
            return null;
        }).when(publisher).publish(any(ScreeningRequestMessage.class));
        ScreeningRequestService service = new ScreeningRequestService(
            repository,
            (instruction, type) -> Optional.of(new ScreeningRequestPolicy(
                "aml-provider", "aml-v3", "aml-policy-9", Map.of("caseRef", "opaque-case"),
                java.util.Set.of(ScreeningDecision.APPROVED, ScreeningDecision.REVIEW_REQUIRED)
            )),
            publisher
        );

        service.createAndPublish(instruction(), ScreeningType.AML);

        ScreeningRequestMessage message = published.get(0);
        assertEquals("transfer-1:AML", message.screeningRequestId());
        assertEquals("transfer-1", message.instructionId());
        assertEquals("aml-policy-9", message.policyVersion());
        assertEquals("aml-v3", message.screeningVersion());
        assertEquals(Map.of("caseRef", "opaque-case"), message.authorizedReferences());
        assertEquals(List.of("APPROVED", "REVIEW_REQUIRED"), stored.get().getAcceptableDecisions());
    }

    @Test
    void policyCannotMarkRejectedOrErroneousResultsAcceptable() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
            new ScreeningRequestPolicy("p", "v", "pv", null, java.util.Set.of(ScreeningDecision.ERROR)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
            new ScreeningRequestPolicy("p", "v", "pv", null, java.util.Set.of(ScreeningDecision.REJECTED)));
        assertEquals(
            java.util.Set.of(ScreeningDecision.APPROVED),
            new ScreeningRequestPolicy("p", "v").acceptableDecisions()
        );
    }

    @Test
    void sanctionsRequestIsCorrelatedMinimizedAndCannotAcceptReview() {
        ScreeningRequestRepository repository = mock(ScreeningRequestRepository.class);
        ScreeningRequestPublisher publisher = mock(ScreeningRequestPublisher.class);
        AtomicReference<ScreeningRequestRecord> stored = new AtomicReference<>();
        List<ScreeningRequestMessage> published = new ArrayList<>();
        when(repository.findByRequestId("transfer-1:SANCTIONS")).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(repository.insert(any(ScreeningRequestRecord.class))).thenAnswer(call -> {
            stored.set(call.getArgument(0));
            return call.getArgument(0);
        });
        when(repository.save(any(ScreeningRequestRecord.class))).thenAnswer(call -> call.getArgument(0));
        org.mockito.Mockito.doAnswer(call -> {
            published.add(call.getArgument(0));
            return null;
        }).when(publisher).publish(any(ScreeningRequestMessage.class));

        ScreeningRequestService accepted = new ScreeningRequestService(
            repository,
            (instruction, type) -> Optional.of(new ScreeningRequestPolicy(
                "sanctions-provider", "s-v1", "s-policy-2", Map.of("partyRef", "opaque-party"), null
            )),
            publisher
        );
        assertTrue(accepted.createAndPublish(instruction(), ScreeningType.SANCTIONS).ready());
        ScreeningRequestMessage message = published.get(0);
        assertEquals("transfer-1:SANCTIONS", message.screeningRequestId());
        assertEquals(ScreeningType.SANCTIONS, message.screeningType());
        assertEquals("s-policy-2", message.policyVersion());
        assertEquals(Map.of("partyRef", "opaque-party"), message.authorizedReferences());

        stored.set(null);
        published.clear();
        ScreeningRequestService lenient = new ScreeningRequestService(
            repository,
            (instruction, type) -> Optional.of(new ScreeningRequestPolicy(
                "sanctions-provider", "s-v1", "s-policy-2", null,
                java.util.Set.of(ScreeningDecision.APPROVED, ScreeningDecision.REVIEW_REQUIRED)
            )),
            publisher
        );
        assertFalse(lenient.createAndPublish(instruction(), ScreeningType.SANCTIONS).ready());
        assertTrue(published.isEmpty());
        assertEquals(ScreeningRequestStatus.POLICY_UNAVAILABLE.name(), stored.get().getPublicationStatus());
    }

    @Test
    void missingProviderPolicyIsDurableNonApprovalAndDoesNotPublish() {
        ScreeningRequestRepository repository = mock(ScreeningRequestRepository.class);
        ScreeningRequestPublisher publisher = mock(ScreeningRequestPublisher.class);
        AtomicReference<ScreeningRequestRecord> stored = new AtomicReference<>();
        when(repository.findByRequestId("transfer-1:SANCTIONS"))
            .thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(repository.insert(any(ScreeningRequestRecord.class))).thenAnswer(call -> {
            ScreeningRequestRecord record = call.getArgument(0);
            stored.set(record);
            return record;
        });
        ScreeningRequestService service = new ScreeningRequestService(
            repository, (instruction, type) -> Optional.empty(), publisher
        );

        ScreeningRequestService.RequestOutcome outcome =
            service.createAndPublish(instruction(), ScreeningType.SANCTIONS);

        assertFalse(outcome.ready());
        assertEquals("SCREENING_POLICY_UNAVAILABLE", outcome.reasonCode());
        assertEquals(ScreeningRequestStatus.POLICY_UNAVAILABLE.name(), stored.get().getPublicationStatus());
        verify(publisher, never()).publish(any());
    }

    @Test
    void kafkaFailureLeavesDurableNonExecutableRequestState() {
        ScreeningRequestRepository repository = mock(ScreeningRequestRepository.class);
        ScreeningRequestPublisher publisher = mock(ScreeningRequestPublisher.class);
        AtomicReference<ScreeningRequestRecord> stored = new AtomicReference<>();
        when(repository.findByRequestId("transfer-1:AML"))
            .thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(repository.insert(any(ScreeningRequestRecord.class))).thenAnswer(call -> {
            ScreeningRequestRecord record = call.getArgument(0);
            stored.set(record);
            return record;
        });
        when(repository.save(any(ScreeningRequestRecord.class))).thenAnswer(call -> {
            ScreeningRequestRecord record = call.getArgument(0);
            stored.set(record);
            return record;
        });
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(any());
        ScreeningRequestService service = new ScreeningRequestService(
            repository,
            (instruction, type) -> Optional.of(new ScreeningRequestPolicy("approved-provider", "policy-v7")),
            publisher
        );

        ScreeningRequestService.RequestOutcome outcome =
            service.createAndPublish(instruction(), ScreeningType.AML);

        assertFalse(outcome.ready());
        assertEquals("SCREENING_REQUEST_PUBLICATION_FAILED", outcome.reasonCode());
        assertEquals(ScreeningRequestStatus.PUBLICATION_FAILED.name(), stored.get().getPublicationStatus());
    }

    private TransferInstruction instruction() {
        TransferInstruction instruction = new TransferInstruction();
        instruction.setInstructionId("transfer-1");
        instruction.setCorrelationId("transfer-1");
        return instruction;
    }

    private ScreeningRequestRecord request(
        String requestId,
        String subjectReference,
        ScreeningRequestStatus status
    ) {
        return new ScreeningRequestRecord(
            requestId,
            requestId + ":correlation",
            "transfer-1",
            "provider-1",
            "KYC",
            "provider-v2",
            Instant.parse("2026-10-15T12:00:00Z"),
            subjectReference,
            Map.of("caseRef", "opaque-" + subjectReference),
            "policy-v4",
            2,
            status,
            null
        );
    }
}
