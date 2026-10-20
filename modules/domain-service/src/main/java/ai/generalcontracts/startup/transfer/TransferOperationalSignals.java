package ai.generalcontracts.startup.transfer;

import java.util.Optional;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Emits secret-free operational signals for transfer states that need operator attention. */
public final class TransferOperationalSignals {
    private static final Log LOG = LogFactory.getLog(TransferOperationalSignals.class);

    private TransferOperationalSignals() {
    }

    public static Optional<String> signalFor(TransferStatus status) {
        if (status == null) {
            return Optional.empty();
        }
        return switch (status) {
            case WORKFLOW_START_UNKNOWN -> Optional.of("WORKFLOW_FAILURE");
            case COMPLIANCE_HOLD -> Optional.of("SCREENING_TIMEOUT_OR_HOLD");
            case TRANSACTION_UNKNOWN -> Optional.of("UNKNOWN_TRANSACTION");
            case TRANSACTION_REVERTED -> Optional.of("TRANSACTION_REVERTED");
            case RECONCILIATION_MISMATCH -> Optional.of("RECONCILIATION_MISMATCH");
            default -> Optional.empty();
        };
    }

    static void emit(TransferInstruction instruction) {
        signalFor(instruction.getStatus()).ifPresent(signal -> LOG.warn(
            "transfer-signal=" + signal
                + " instructionId=" + instruction.getInstructionId()
                + " correlationId=" + instruction.getCorrelationId()
                + " status=" + instruction.getStatus()
                + " reasonCode=" + instruction.getReasonCode()
        ));
    }
}