package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.startup.transfer.TransferInstruction;
import ai.generalcontracts.startup.transfer.TransferInstructionRepository;
import ai.generalcontracts.startup.transfer.TransferStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ai.generalcontracts.startup.transfer.TransferAuditRecord;
import ai.generalcontracts.startup.transfer.TransferAuditRepository;
import ai.generalcontracts.startup.transfer.WorkflowLaunchState;

@Service
public class TransferLifecycleService {
    private final TransferInstructionRepository repository;
    private final TransferAuditRepository auditRepository;
    private final Clock clock;

    @Autowired
    public TransferLifecycleService(
        TransferInstructionRepository repository,
        TransferAuditRepository auditRepository
    ) {
        this(repository, auditRepository, Clock.systemUTC());
    }

    public TransferLifecycleService(TransferInstructionRepository repository) {
        this(repository, null, Clock.systemUTC());
    }

    TransferLifecycleService(TransferInstructionRepository repository, TransferAuditRepository auditRepository, Clock clock) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    public TransferInstruction create(TransferCreateCommand command) {
        String amount = canonicalAmount(command.amount());
        String fingerprint = fingerprint(command, amount);
        Optional<TransferInstruction> existing = repository
            .findByTenantIdAndInitiatingPrincipalAndIdempotencyKey(
                command.tenantId(), command.initiatingPrincipal(), command.idempotencyKey()
            );
        if (existing.isPresent()) {
            return existingOrConflict(existing.get(), fingerprint);
        }

        Instant now = clock.instant();
        TransferInstruction instruction = new TransferInstruction();
        instruction.setInstructionId(UUID.randomUUID().toString());
        instruction.setTenantId(command.tenantId());
        instruction.setInitiatingPrincipal(command.initiatingPrincipal());
        instruction.setIdempotencyKey(command.idempotencyKey());
        instruction.setRequestFingerprint(fingerprint);
        instruction.setSourceWallet(canonicalIdentifier(command.sourceWallet()));
        instruction.setDestinationWallet(canonicalIdentifier(command.destinationWallet()));
        instruction.setTokenIdentifier(canonicalTokenIdentifier(command.tokenIdentifier()));
        instruction.setAmount(amount);
        instruction.setNetworkId(command.networkId());
        instruction.setStatus(TransferStatus.RECEIVED);
        instruction.setWorkflowLaunchState(WorkflowLaunchState.NOT_STARTED);
        instruction.setCorrelationId(instruction.getInstructionId());
        instruction.setCreatedAt(now);
        instruction.setUpdatedAt(now);
        try {
            TransferInstruction saved = repository.save(instruction);
            appendAudit(saved, "TransferInstructionCreated", null, command.initiatingPrincipal());
            return saved;
        } catch (DuplicateKeyException race) {
            return repository.findByTenantIdAndInitiatingPrincipalAndIdempotencyKey(
                    command.tenantId(), command.initiatingPrincipal(), command.idempotencyKey()
                )
                .map(found -> existingOrConflict(found, fingerprint))
                .orElseThrow(() -> race);
        }
    }

    public TransferInstruction get(String instructionId) {
        return repository.findById(instructionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer instruction not found"));
    }

    public TransferInstruction startWorkflow(String instructionId, TransferWorkflowStarter starter) {
        TransferInstruction instruction = get(instructionId);
        if (instruction.getWorkflowLaunchState() == WorkflowLaunchState.STARTED
            || instruction.getWorkflowLaunchState() == WorkflowLaunchState.STARTING
            || instruction.getWorkflowLaunchState() == WorkflowLaunchState.UNKNOWN) {
            return instruction;
        }
        instruction.setWorkflowLaunchState(WorkflowLaunchState.STARTING);
        instruction.setUpdatedAt(clock.instant());
        TransferInstruction claimed;
        try {
            claimed = repository.save(instruction);
        } catch (OptimisticLockingFailureException concurrentClaim) {
            return get(instructionId);
        }
        try {
            starter.start(claimed);
            claimed.setWorkflowLaunchState(WorkflowLaunchState.STARTED);
            claimed.setUpdatedAt(clock.instant());
            TransferInstruction started = repository.save(claimed);
            appendAudit(started, "TransferWorkflowStarted", null, "system");
            return started;
        } catch (RuntimeException uncertain) {
            claimed.setWorkflowLaunchState(WorkflowLaunchState.UNKNOWN);
            claimed.setStatus(TransferStatus.WORKFLOW_START_UNKNOWN);
            claimed.setReasonCode("WORKFLOW_START_OUTCOME_UNKNOWN");
            claimed.setUpdatedAt(clock.instant());
            TransferInstruction held = repository.save(claimed);
            appendAudit(held, "TransferWorkflowStartUnknown", held.getReasonCode(), "system");
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Transfer workflow start outcome is unknown"
            );
        }
    }

    public TransferInstruction transition(
        String instructionId,
        TransferStatus expected,
        TransferStatus next,
        String reasonCode,
        String transactionHash
    ) {
        if (!isAllowed(expected, next)) {
            throw new IllegalArgumentException("Unsupported transfer status transition");
        }
        TransferInstruction current = get(instructionId);
        if (current.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer state changed");
        }
        current.setStatus(next);
        current.setReasonCode(reasonCode);
        current.setTransactionHash(transactionHash);
        current.setUpdatedAt(clock.instant());
        try {
            TransferInstruction saved = repository.save(current);
            appendAudit(saved, "TransferStatusChanged", reasonCode, "system");
            return saved;
        } catch (OptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer state changed", conflict);
        }
    }

    private void appendAudit(TransferInstruction instruction, String eventType, String reasonCode, String actor) {
        if (auditRepository == null) {
            return;
        }
        auditRepository.save(new TransferAuditRecord(
            UUID.randomUUID().toString(),
            instruction.getInstructionId(),
            instruction.getCorrelationId(),
            eventType,
            instruction.getStatus().name(),
            reasonCode,
            actor,
            clock.instant()
        ));
    }

    private TransferInstruction existingOrConflict(TransferInstruction current, String fingerprint) {
        if (!fingerprint.equals(current.getRequestFingerprint())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was used for different transfer data");
        }
        return current;
    }

    private String canonicalAmount(String raw) {
        try {
            BigDecimal amount = new BigDecimal(raw);
            int integerDigits = amount.precision() - amount.scale();
            if (amount.signum() <= 0 || integerDigits > 36 || amount.scale() > 18) {
                throw new IllegalArgumentException("Amount is outside supported exact decimal range");
            }
            return amount.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Amount must be an exact decimal value", exception);
        }
    }

    private String canonicalIdentifier(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String canonicalTokenIdentifier(String value) {
        String trimmed = value.trim();
        return trimmed.matches("(?i)^0x[0-9a-f]{40}$")
            ? trimmed.toLowerCase(java.util.Locale.ROOT)
            : trimmed;
    }

    private String fingerprint(TransferCreateCommand command, String amount) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            for (String value : new String[] {
            canonicalIdentifier(command.sourceWallet()),
            canonicalIdentifier(command.destinationWallet()),
            canonicalTokenIdentifier(command.tokenIdentifier()),
            amount,
            Long.toString(command.networkId())
            }) {
                byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                output.writeInt(encoded.length);
                output.write(encoded);
            }
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            );
        } catch (NoSuchAlgorithmException | IOException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private boolean isAllowed(TransferStatus from, TransferStatus to) {
        return switch (from) {
            case RECEIVED -> to == TransferStatus.VALIDATING || to == TransferStatus.CANCELLED
                || to == TransferStatus.WORKFLOW_START_UNKNOWN;
            case WORKFLOW_START_UNKNOWN -> to == TransferStatus.CANCELLED;
            case VALIDATING -> to == TransferStatus.VALIDATION_FAILED
                || to == TransferStatus.CANCELLED
                || to == TransferStatus.SCREENING_PENDING || to == TransferStatus.COMPLIANCE_HOLD;
            case SCREENING_PENDING -> to == TransferStatus.COMPLIANCE_APPROVED
                || to == TransferStatus.COMPLIANCE_REJECTED || to == TransferStatus.MANUAL_REVIEW
                || to == TransferStatus.COMPLIANCE_HOLD || to == TransferStatus.CANCELLED;
            case VALIDATION_FAILED, COMPLIANCE_REJECTED, MANUAL_REVIEW, COMPLIANCE_HOLD ->
                to == TransferStatus.CANCELLED;
            case COMPLIANCE_APPROVED -> to == TransferStatus.READY_FOR_EXECUTION
                || to == TransferStatus.COMPLIANCE_HOLD || to == TransferStatus.CANCELLED;
            case READY_FOR_EXECUTION -> to == TransferStatus.SUBMISSION_IN_PROGRESS
                || to == TransferStatus.TRANSACTION_UNKNOWN || to == TransferStatus.COMPLIANCE_HOLD
                || to == TransferStatus.CANCELLED;
            case SUBMISSION_IN_PROGRESS -> to == TransferStatus.TRANSACTION_SUBMITTED
                || to == TransferStatus.TRANSACTION_UNKNOWN;
            case TRANSACTION_SUBMITTED -> to == TransferStatus.TRANSACTION_PENDING
                || to == TransferStatus.TRANSACTION_REVERTED || to == TransferStatus.TRANSACTION_UNKNOWN;
            case TRANSACTION_PENDING -> to == TransferStatus.TRANSACTION_CONFIRMED
                || to == TransferStatus.TRANSACTION_REVERTED || to == TransferStatus.TRANSACTION_UNKNOWN;
            case TRANSACTION_CONFIRMED -> to == TransferStatus.RECONCILIATION_PENDING;
            case RECONCILIATION_PENDING -> to == TransferStatus.COMPLETED
                || to == TransferStatus.RECONCILIATION_MISMATCH;
            case TRANSACTION_UNKNOWN, RECONCILIATION_MISMATCH -> to == TransferStatus.COMPLIANCE_HOLD;
            default -> false;
        };
    }
}
