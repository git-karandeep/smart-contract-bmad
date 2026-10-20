package ai.generalcontracts.startup.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransferAuditTrailTest {
    @Test
    void statusChangeAuditCarriesCorrelationReasonAndTransactionReference() {
        TransferInstructionRepository repository = mock(TransferInstructionRepository.class);
        TransferAuditRepository audit = mock(TransferAuditRepository.class);
        TransferInstruction instruction = new TransferInstruction();
        instruction.setInstructionId("i-1");
        instruction.setCorrelationId("c-1");
        instruction.setStatus(TransferStatus.RECONCILIATION_PENDING);
        when(repository.findById("i-1")).thenReturn(Optional.of(instruction));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        TransferLifecycleService service = new TransferLifecycleService(repository, audit);

        service.transition(
            "i-1", TransferStatus.RECONCILIATION_PENDING, TransferStatus.RECONCILIATION_MISMATCH, "MISMATCH", "0xabc"
        );

        ArgumentCaptor<TransferAuditRecord> saved = ArgumentCaptor.forClass(TransferAuditRecord.class);
        verify(audit).save(saved.capture());
        TransferAuditRecord record = saved.getValue();
        assertEquals("i-1", record.getInstructionId());
        assertEquals("c-1", record.getCorrelationId());
        assertEquals("MISMATCH", record.getReasonCode());
        assertEquals("0xabc", record.getTransactionHash());
        assertEquals("RECONCILIATION_MISMATCH", record.getStatus());
    }

    @Test
    void auditRecordExposesNoSecretOrIdentityFields() {
        for (var field : TransferAuditRecord.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("key") || name.contains("secret") || name.contains("wallet")
                || name.contains("password"), name);
        }
    }

    @Test
    void signalsCoverFailurePathsAndIgnoreHealthyStates() {
        assertEquals("UNKNOWN_TRANSACTION", TransferOperationalSignals.signalFor(TransferStatus.TRANSACTION_UNKNOWN).get());
        assertEquals("RECONCILIATION_MISMATCH",
            TransferOperationalSignals.signalFor(TransferStatus.RECONCILIATION_MISMATCH).get());
        assertEquals("WORKFLOW_FAILURE", TransferOperationalSignals.signalFor(TransferStatus.WORKFLOW_START_UNKNOWN).get());
        assertEquals("SCREENING_TIMEOUT_OR_HOLD", TransferOperationalSignals.signalFor(TransferStatus.COMPLIANCE_HOLD).get());
        assertTrue(TransferOperationalSignals.signalFor(TransferStatus.COMPLETED).isEmpty());
    }
}