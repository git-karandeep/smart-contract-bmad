package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ScreeningRequestServiceTest {
    @Test
    void createsOnceAndPublishesAStableProviderNeutralRequest() {
        ScreeningRequestRepository repository = mock(ScreeningRequestRepository.class);
        ScreeningRequestPublisher publisher = mock(ScreeningRequestPublisher.class);
        AtomicReference<ScreeningRequestRecord> stored = new AtomicReference<>();
        when(repository.findByRequestId("transfer-1:KYC"))
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

        ScreeningRequestService.RequestOutcome first = service.createAndPublish(instruction(), ScreeningType.KYC);
        ScreeningRequestService.RequestOutcome duplicate = service.createAndPublish(instruction(), ScreeningType.KYC);

        assertTrue(first.ready());
        assertEquals(first.requestId(), duplicate.requestId());
        assertEquals("transfer-1:KYC", first.requestId());
        assertEquals(ScreeningRequestStatus.PUBLISHED.name(), stored.get().getPublicationStatus());
        verify(repository).insert(any(ScreeningRequestRecord.class));
        verify(publisher).publish(any(ScreeningRequestMessage.class));
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
}
