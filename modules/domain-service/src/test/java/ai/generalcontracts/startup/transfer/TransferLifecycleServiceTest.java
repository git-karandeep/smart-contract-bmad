package ai.generalcontracts.startup.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import ai.generalcontracts.startup.transfer.TransferCreateCommand;
import ai.generalcontracts.startup.transfer.TransferLifecycleService;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import ai.generalcontracts.startup.transfer.TransferInstructionRepository;
import ai.generalcontracts.startup.transfer.TransferStatus;
import ai.generalcontracts.startup.transfer.WorkflowLaunchState;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TransferLifecycleServiceTest {
    @Mock
    private TransferInstructionRepository repository;

    private TransferLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new TransferLifecycleService(repository);
    }

    @Test
    void storesExactCanonicalAmountAndStartsInReceivedState() {
        when(repository.findByTenantIdAndInitiatingPrincipalAndIdempotencyKey("tenant", "principal", "key"))
            .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransferInstruction created = service.create(command("10.2500"));

        assertEquals("10.25", created.getAmount());
        assertEquals(TransferStatus.RECEIVED, created.getStatus());
        assertEquals(created.getInstructionId(), created.getCorrelationId());
        verify(repository).save(any(TransferInstruction.class));
    }

    @Test
    void returnsExistingInstructionForEquivalentRequest() {
        when(repository.findByTenantIdAndInitiatingPrincipalAndIdempotencyKey("tenant", "principal", "key"))
            .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransferInstruction existing = service.create(command("10.25"));
        when(repository.findByTenantIdAndInitiatingPrincipalAndIdempotencyKey("tenant", "principal", "key"))
            .thenReturn(Optional.of(existing));

        assertEquals(existing, service.create(command("10.2500")));
        verify(repository, times(1)).save(any());
    }

    @Test
    void concurrentDuplicateCreateReturnsTheWinnerAndConflictingRaceIsRejected() {
        when(repository.findByTenantIdAndInitiatingPrincipalAndIdempotencyKey("tenant", "principal", "key"))
            .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransferInstruction winner = service.create(command("10.25"));
        org.mockito.Mockito.reset(repository);
        when(repository.findByTenantIdAndInitiatingPrincipalAndIdempotencyKey("tenant", "principal", "key"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(winner))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(winner));
        when(repository.save(any())).thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));

        assertEquals(winner, service.create(command("10.2500")));
        assertThrows(ResponseStatusException.class, () -> service.create(command("11")));
    }

    @Test
    void rejectsIdempotencyKeyReuseForDifferentInstructionData() {
        when(repository.findByTenantIdAndInitiatingPrincipalAndIdempotencyKey("tenant", "principal", "key"))
            .thenReturn(Optional.of(instruction("10.25")));

        assertThrows(ResponseStatusException.class, () -> service.create(command("11")));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsFloatingPointLikeInvalidAndNonPositiveAmountsWithoutPersistence() {
        assertThrows(IllegalArgumentException.class, () -> service.create(command("NaN")));
        assertThrows(IllegalArgumentException.class, () -> service.create(command("0")));
        assertThrows(IllegalArgumentException.class, () -> service.create(command("1.1234567890123456789")));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsIllegalLifecycleTransitions() {
        assertThrows(IllegalArgumentException.class, () -> service.transition(
            "transfer-id", TransferStatus.RECEIVED, TransferStatus.COMPLETED, null, null
        ));
        verify(repository, never()).save(any());
    }

    @Test
    void persistsOnlyAllowedStateTransitions() {
        TransferInstruction received = instruction("10.25");
        when(repository.findById("transfer-id")).thenReturn(Optional.of(received));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransferInstruction updated = service.transition(
            "transfer-id", TransferStatus.RECEIVED, TransferStatus.VALIDATING, null, null
        );

        assertEquals(TransferStatus.VALIDATING, updated.getStatus());
        verify(repository).save(received);
    }

    @Test
    void permitsCancellationFromReadyButNotAfterSubmissionHasStarted() {
        assertThrows(IllegalArgumentException.class, () -> service.transition(
            "transfer-id", TransferStatus.SUBMISSION_IN_PROGRESS, TransferStatus.CANCELLED, null, null
        ));
        TransferInstruction ready = instruction("10.25");
        ready.setStatus(TransferStatus.READY_FOR_EXECUTION);
        when(repository.findById("transfer-id")).thenReturn(Optional.of(ready));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransferInstruction cancelled = service.transition(
            "transfer-id", TransferStatus.READY_FOR_EXECUTION, TransferStatus.CANCELLED, "TRANSFER_CANCELLED", null
        );

        assertEquals(TransferStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void convertsAnOptimisticLockRaceIntoAStateConflict() {
        TransferInstruction ready = instruction("10.25");
        ready.setStatus(TransferStatus.READY_FOR_EXECUTION);
        when(repository.findById("transfer-id")).thenReturn(Optional.of(ready));
        when(repository.save(any())).thenThrow(new OptimisticLockingFailureException("Concurrent state update"));

        assertThrows(ResponseStatusException.class, () -> service.transition(
            "transfer-id",
            TransferStatus.READY_FOR_EXECUTION,
            TransferStatus.CANCELLED,
            "TRANSFER_CANCELLED",
            null
        ));
    }

    @Test
    void fingerprintsDifferentNetworksSeparately() {
        TransferCreateCommand first = command("10.25");
        TransferCreateCommand second = new TransferCreateCommand(
            first.sourceWallet(), first.destinationWallet(), first.tokenIdentifier(), first.amount(), 2,
            first.idempotencyKey(), first.tenantId(), first.initiatingPrincipal()
        );
        when(repository.findByTenantIdAndInitiatingPrincipalAndIdempotencyKey("tenant", "principal", "key"))
            .thenReturn(Optional.empty(), Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransferInstruction firstCreated = service.create(first);
        TransferInstruction secondCreated = service.create(second);

        assertNotEquals(firstCreated.getRequestFingerprint(), secondCreated.getRequestFingerprint());
    }

    @Test
    void claimsWorkflowStartBeforeCallingTheStarterAndDoesNotStartTwice() {
        TransferInstruction received = instruction("10.25");
        received.setWorkflowLaunchState(WorkflowLaunchState.NOT_STARTED);
        when(repository.findById("transfer-id")).thenReturn(Optional.of(received));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransferWorkflowStarter starter = org.mockito.Mockito.mock(TransferWorkflowStarter.class);

        TransferInstruction started = service.startWorkflow("transfer-id", starter);
        TransferInstruction secondCall = service.startWorkflow("transfer-id", starter);

        assertEquals(WorkflowLaunchState.STARTED, started.getWorkflowLaunchState());
        assertEquals(WorkflowLaunchState.STARTED, secondCall.getWorkflowLaunchState());
        verify(starter, times(1)).start(received);
    }

    @Test
    void holdsWhenWorkflowStartOutcomeIsUnknownAndDoesNotRetryBlindly() {
        TransferInstruction received = instruction("10.25");
        received.setWorkflowLaunchState(WorkflowLaunchState.NOT_STARTED);
        when(repository.findById("transfer-id")).thenReturn(Optional.of(received));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransferWorkflowStarter starter = org.mockito.Mockito.mock(TransferWorkflowStarter.class);
        doThrow(new IllegalStateException("connection failure")).when(starter).start(any());

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.startWorkflow("transfer-id", starter));

        assertEquals(WorkflowLaunchState.UNKNOWN, received.getWorkflowLaunchState());
        assertEquals(TransferStatus.WORKFLOW_START_UNKNOWN, received.getStatus());
        assertEquals("WORKFLOW_START_OUTCOME_UNKNOWN", received.getReasonCode());
        verify(starter, times(1)).start(received);
    }

    private TransferCreateCommand command(String amount) {
        return new TransferCreateCommand(
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222",
            "0x3333333333333333333333333333333333333333",
            amount, 1337, "key", "tenant", "principal"
        );
    }

    private TransferInstruction instruction(String amount) {
        TransferCreateCommand command = command(amount);
        TransferInstruction instruction = new TransferInstruction();
        instruction.setInstructionId("transfer-id");
        instruction.setTenantId("tenant");
        instruction.setInitiatingPrincipal("principal");
        instruction.setIdempotencyKey("key");
        instruction.setRequestFingerprint("placeholder");
        instruction.setSourceWallet(command.sourceWallet());
        instruction.setDestinationWallet(command.destinationWallet());
        instruction.setTokenIdentifier(command.tokenIdentifier());
        instruction.setAmount(amount);
        instruction.setNetworkId(command.networkId());
        instruction.setStatus(TransferStatus.RECEIVED);
        instruction.setWorkflowLaunchState(WorkflowLaunchState.NOT_STARTED);
        return instruction;
    }
}
