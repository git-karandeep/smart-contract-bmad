package ai.generalcontracts.startup.workflow;

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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransferLifecycleJobHandlers {
    private final TransferInstructionRepository instructions;
    private final ScreeningRequestRepository requests;
    private final ScreeningResultRepository results;
    private final TransferLifecycleService lifecycle;
    private final TransferValidationPolicy validationPolicy;
    private final ScreeningRequestService screeningRequests;
    private final ScreeningDecisionAggregator decisionAggregator;
    private final PreExecutionPolicy preExecutionPolicy;
    private final TransferSubmissionPort submissionPort;
    private final TransferReconciliationPort reconciliationPort;

    public TransferLifecycleJobHandlers(
        TransferInstructionRepository instructions,
        ScreeningRequestRepository requests,
        ScreeningResultRepository results,
        TransferLifecycleService lifecycle,
        TransferValidationPolicy validationPolicy,
        ScreeningRequestService screeningRequests,
        ScreeningDecisionAggregator decisionAggregator,
        PreExecutionPolicy preExecutionPolicy,
        TransferSubmissionPort submissionPort,
        TransferReconciliationPort reconciliationPort
    ) {
        this.instructions = instructions;
        this.requests = requests;
        this.results = results;
        this.lifecycle = lifecycle;
        this.validationPolicy = validationPolicy;
        this.screeningRequests = screeningRequests;
        this.decisionAggregator = decisionAggregator;
        this.preExecutionPolicy = preExecutionPolicy;
        this.submissionPort = submissionPort;
        this.reconciliationPort = reconciliationPort;
    }

    public Map<String, Object> validate(String instructionId) {
        TransferInstruction instruction = instruction(instructionId);
        if (instruction.getStatus() == TransferStatus.SCREENING_PENDING) {
            return output("validationPassed", true);
        }
        if (instruction.getStatus() == TransferStatus.VALIDATION_FAILED
            || instruction.getStatus() == TransferStatus.COMPLIANCE_HOLD
            || instruction.getStatus() != TransferStatus.RECEIVED
                && instruction.getStatus() != TransferStatus.VALIDATING) {
            return rejectedValidation(instruction.getReasonCode() == null
                ? "TRANSFER_NOT_VALIDATABLE"
                : instruction.getReasonCode());
        }
        if (instruction.getStatus() == TransferStatus.RECEIVED) {
            instruction = transition(instruction, TransferStatus.RECEIVED, TransferStatus.VALIDATING, null, null);
        }

        TransferValidationPolicy.ValidationDecision decision;
        try {
            decision = validationPolicy.validate(instruction);
        } catch (RuntimeException unavailable) {
            decision = TransferValidationPolicy.ValidationDecision.rejected(
                "TRANSFER_VALIDATION_POLICY_UNAVAILABLE"
            );
        }
        if (decision == null || !decision.valid()) {
            String reason = decision == null || blank(decision.reasonCode())
                ? "TRANSFER_VALIDATION_POLICY_UNAVAILABLE"
                : decision.reasonCode();
            transition(instruction, TransferStatus.VALIDATING, TransferStatus.VALIDATION_FAILED, reason, null);
            return rejectedValidation(reason);
        }
        transition(instruction, TransferStatus.VALIDATING, TransferStatus.SCREENING_PENDING, null, null);
        return output("validationPassed", true);
    }

    public Map<String, Object> requestScreening(String instructionId, ScreeningType type) {
        TransferInstruction instruction = instruction(instructionId);
        if (instruction.getStatus() != TransferStatus.SCREENING_PENDING) {
            return screeningRequestOutput(type, false, null, instruction.getReasonCode() == null
                ? "TRANSFER_NOT_SCREENING"
                : instruction.getReasonCode());
        }
        ScreeningRequestService.RequestOutcome outcome = type == ScreeningType.KYC
            ? screeningRequests.createKycRequests(instruction)
            : screeningRequests.createAndPublish(instruction, type);
        Map<String, Object> output = screeningRequestOutput(
            type, outcome.ready(), outcome.requestId(), outcome.reasonCode()
        );
        if (type == ScreeningType.KYC && outcome.ready()) {
            output.put("kycNotRequired", outcome.notRequired());
            if (!outcome.notRequired()) {
                output.put("kycScreeningRequestIds", outcome.requestIds());
            }
        }
        return output;
    }

    public Map<String, Object> recordScreeningTimeout(String instructionId, ScreeningType type) {
        ScreeningRequestRecord request = screeningRequests.recordTimeout(instructionId, type);
        return screeningTimeoutOutput(type, request);
    }

    public Map<String, Object> recordScreeningTimeout(
        String instructionId,
        ScreeningType type,
        String requestId
    ) {
        ScreeningRequestRecord request = screeningRequests.recordTimeout(instructionId, type, requestId);
        return screeningTimeoutOutput(type, request);
    }

    private Map<String, Object> screeningTimeoutOutput(ScreeningType type, ScreeningRequestRecord request) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put(timeoutVariable(type), true);
        output.put("screeningHoldPersisted", true);
        output.put("screeningRequestId", request.getRequestId());
        return output;
    }

    public Map<String, Object> aggregateCompliance(String instructionId) {
        TransferInstruction instruction = instruction(instructionId);
        EnumMap<ScreeningType, List<ScreeningDecision>> decisions = new EnumMap<>(ScreeningType.class);
        Set<ScreeningType> requiredTypes = EnumSet.allOf(ScreeningType.class);
        boolean holdPersisted = instruction.getStatus() == TransferStatus.COMPLIANCE_HOLD;
        for (ScreeningType type : ScreeningType.values()) {
            List<ScreeningRequestRecord> typeRequests = requestRecords(instructionId, type);
            if (type == ScreeningType.KYC && typeRequests.size() == 1
                && ScreeningRequestStatus.NOT_REQUIRED.name().equals(typeRequests.get(0).getPublicationStatus())) {
                requiredTypes.remove(ScreeningType.KYC);
                continue;
            }
            if (typeRequests.isEmpty()) {
                holdPersisted = true;
                continue;
            }
            List<ScreeningResultRecord> typeResults = resultRecords(instructionId, type);
            Map<String, ScreeningResultRecord> resultByRequestId = new HashMap<>();
            for (ScreeningResultRecord result : typeResults) {
                if (resultByRequestId.put(result.getRequestId(), result) != null) {
                    holdPersisted = true;
                }
            }
            if (type == ScreeningType.KYC) {
                Integer expectedCount = typeRequests.get(0).getRequiredSubjectCount();
                if (expectedCount == null || expectedCount != typeRequests.size()
                    || typeRequests.stream().anyMatch(request ->
                        !expectedCount.equals(request.getRequiredSubjectCount())
                            || request.getSubjectReference() == null)) {
                    holdPersisted = true;
                }
            } else if (typeRequests.size() != 1) {
                holdPersisted = true;
            }
            List<ScreeningDecision> typeDecisions = new ArrayList<>();
            for (ScreeningRequestRecord request : typeRequests) {
                if (!ScreeningRequestStatus.PUBLISHED.name().equals(request.getPublicationStatus())) {
                    holdPersisted = true;
                }
                ScreeningResultRecord result = resultByRequestId.get(request.getRequestId());
                if (result != null) {
                    typeDecisions.add(effectiveDecision(request, result));
                } else {
                    holdPersisted = true;
                }
            }
            if (typeResults.size() != typeRequests.size()) {
                holdPersisted = true;
            }
            if (!typeDecisions.isEmpty()) {
                decisions.put(type, typeDecisions);
            }
        }

        ComplianceOutcome outcome = decisionAggregator.evaluate(decisions, requiredTypes, holdPersisted);
        recordComplianceEvaluation(instruction, decisions, requiredTypes, outcome, holdPersisted);
        persistComplianceOutcome(instruction, outcome);
        Map<String, Object> output = output("complianceOutcome", outcome.name());
        output.put("complianceApproved", outcome == ComplianceOutcome.APPROVED);
        return output;
    }

    public Map<String, Object> preExecutionGate(String instructionId, String complianceOutcome) {
        TransferInstruction instruction = instruction(instructionId);
        String failure = preExecutionFailure(instruction, complianceOutcome);
        boolean allowed = failure == null;
        if (allowed && instruction.getStatus() == TransferStatus.COMPLIANCE_APPROVED) {
            transition(instruction, TransferStatus.COMPLIANCE_APPROVED, TransferStatus.READY_FOR_EXECUTION, null, null);
        } else if (!allowed && (instruction.getStatus() == TransferStatus.COMPLIANCE_APPROVED
            || instruction.getStatus() == TransferStatus.READY_FOR_EXECUTION)) {
            transition(instruction, instruction.getStatus(), TransferStatus.COMPLIANCE_HOLD, failure, null);
        }
        Map<String, Object> output = output("executionAllowed", allowed);
        output.put("executionGateReasonCode", allowed ? "PRE_EXECUTION_GATE_PASSED" : failure);
        output.put("executionGateInstructionId", instructionId);
        return output;
    }

    private String preExecutionFailure(TransferInstruction instruction, String complianceOutcome) {
        TransferStatus status = instruction.getStatus();
        if (status == TransferStatus.CANCELLED) {
            return "PRE_EXECUTION_CANCELLED";
        }
        if (status != TransferStatus.COMPLIANCE_APPROVED && status != TransferStatus.READY_FOR_EXECUTION) {
            return "PRE_EXECUTION_STATE_NOT_ELIGIBLE";
        }
        if (!"APPROVED".equals(complianceOutcome)) {
            return "PRE_EXECUTION_COMPLIANCE_NOT_APPROVED";
        }
        if (!allMandatoryApproved(instruction.getInstructionId())) {
            return "PRE_EXECUTION_APPROVAL_MISSING_OR_STALE";
        }
        if (instruction.getApprovedFingerprint() == null
            || !instruction.getApprovedFingerprint().equals(approvalFingerprint(instruction))) {
            return "PRE_EXECUTION_INSTRUCTION_CHANGED";
        }
        try {
            if (!validationPolicy.validate(instruction).valid()) {
                return "PRE_EXECUTION_VALIDATION_FAILED";
            }
        } catch (RuntimeException unavailable) {
            return "PRE_EXECUTION_VALIDATION_FAILED";
        }
        return safePolicyCheck(instruction) ? null : "PRE_EXECUTION_POLICY_UNAVAILABLE_OR_FAILED";
    }

    private static String approvalFingerprint(TransferInstruction instruction) {
        String identity = String.join(
            "\u0000",
            String.valueOf(instruction.getSourceWallet()).toLowerCase(java.util.Locale.ROOT),
            String.valueOf(instruction.getDestinationWallet()).toLowerCase(java.util.Locale.ROOT),
            String.valueOf(instruction.getAmount()),
            String.valueOf(instruction.getTokenIdentifier()).toLowerCase(java.util.Locale.ROOT),
            String.valueOf(instruction.getNetworkId())
        );
        try {
            return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    public Map<String, Object> submit(String instructionId, boolean executionAllowed) {
        TransferInstruction instruction = instruction(instructionId);
        if (executionAllowed && instruction.getStatus() == TransferStatus.TRANSACTION_SUBMITTED
            && !blank(instruction.getTransactionHash())) {
            return submissionOutput("SUBMITTED", instruction.getTransactionHash(), null);
        }
        if (!executionAllowed
            || (instruction.getStatus() != TransferStatus.READY_FOR_EXECUTION
                && instruction.getStatus() != TransferStatus.SUBMISSION_IN_PROGRESS)) {
            return submissionOutput("UNKNOWN", null, "EXECUTION_GATE_NOT_OPEN");
        }

        if (instruction.getStatus() == TransferStatus.READY_FOR_EXECUTION) {
            try {
                lifecycle.transition(
                    instructionId,
                    TransferStatus.READY_FOR_EXECUTION,
                    TransferStatus.SUBMISSION_IN_PROGRESS,
                    null,
                    null
                );
            } catch (ResponseStatusException concurrentOrStaleClaim) {
                return submissionOutput("UNKNOWN", null, "SUBMISSION_CLAIM_NOT_ACQUIRED");
            }
        }
        instruction = instruction(instructionId);
        if (instruction.getStatus() != TransferStatus.SUBMISSION_IN_PROGRESS) {
            return submissionOutput("UNKNOWN", null, "SUBMISSION_STATE_CHANGED");
        }

        TransferSubmission submission;
        try {
            submission = submissionPort.submit(instruction);
        } catch (RuntimeException unavailableOrUncertain) {
            transition(
                instruction,
                TransferStatus.SUBMISSION_IN_PROGRESS,
                TransferStatus.TRANSACTION_UNKNOWN,
                "CHAIN_SUBMISSION_OUTCOME_UNKNOWN",
                null
            );
            return submissionOutput("UNKNOWN", null, "CHAIN_SUBMISSION_OUTCOME_UNKNOWN");
        }
        if (!validSubmission(instruction, submission)) {
            transition(
                instruction,
                TransferStatus.SUBMISSION_IN_PROGRESS,
                TransferStatus.TRANSACTION_UNKNOWN,
                "CHAIN_SUBMISSION_RESPONSE_INVALID_OR_UNKNOWN",
                null
            );
            return submissionOutput("UNKNOWN", null, "CHAIN_SUBMISSION_RESPONSE_INVALID_OR_UNKNOWN");
        }
        if (submission.outcome() != TransferSubmission.Outcome.SUBMITTED
            || blank(submission.transactionHash())) {
            String reason = blank(submission.reasonCode())
                ? "CHAIN_SUBMISSION_OUTCOME_UNKNOWN"
                : submission.reasonCode();
            String knownHash = blank(submission.transactionHash()) ? null : submission.transactionHash();
            transition(
                instruction,
                TransferStatus.SUBMISSION_IN_PROGRESS,
                TransferStatus.TRANSACTION_UNKNOWN,
                reason,
                knownHash
            );
            return submissionOutput("UNKNOWN", knownHash, reason);
        }
        transition(
            instruction,
            TransferStatus.SUBMISSION_IN_PROGRESS,
            TransferStatus.TRANSACTION_SUBMITTED,
            null,
            submission.transactionHash()
        );
        return submissionOutput("SUBMITTED", submission.transactionHash(), null);
    }

    public Map<String, Object> cancel(String instructionId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            TransferStatus status = instruction(instructionId).getStatus();
            if (status == TransferStatus.CANCELLED) {
                return cancellationOutput("CANCELLED", null);
            }
            if (!cancellableBeforeSubmission(status)) {
                String reason = submissionStarted(status)
                    ? "SUBMISSION_ALREADY_STARTED"
                    : "TRANSFER_NOT_CANCELLABLE";
                return cancellationOutput("NOT_CANCELLED", reason);
            }

            try {
                lifecycle.transition(
                    instructionId,
                    status,
                    TransferStatus.CANCELLED,
                    "TRANSFER_CANCELLED",
                    null
                );
                return cancellationOutput("CANCELLED", null);
            } catch (ResponseStatusException concurrentTransition) {
                TransferStatus currentStatus = instruction(instructionId).getStatus();
                if (currentStatus == TransferStatus.CANCELLED) {
                    return cancellationOutput("CANCELLED", null);
                }
                if (!cancellableBeforeSubmission(currentStatus)) {
                    String reason = submissionStarted(currentStatus)
                        ? "SUBMISSION_ALREADY_STARTED"
                        : "TRANSFER_NOT_CANCELLABLE";
                    return cancellationOutput("NOT_CANCELLED", reason);
                }
            }
        }
        return cancellationOutput("NOT_CANCELLED", "TRANSFER_STATE_CHANGED");
    }

    public Map<String, Object> reconcile(
        String instructionId,
        String transactionHash,
        String transactionOutcome,
        boolean finalized
    ) {
        TransferInstruction instruction = instruction(instructionId);
        Map<String, Object> replay = replayTerminalReconciliation(instruction, transactionHash);
        if (replay != null) {
            return replay;
        }
        if ("PENDING".equals(transactionOutcome) && !finalized && !blank(transactionHash)) {
            boolean hashMatches = !blank(instruction.getTransactionHash())
                && instruction.getTransactionHash().equalsIgnoreCase(transactionHash);
            if (hashMatches && instruction.getStatus() == TransferStatus.TRANSACTION_SUBMITTED) {
                transition(
                    instruction,
                    TransferStatus.TRANSACTION_SUBMITTED,
                    TransferStatus.TRANSACTION_PENDING,
                    null,
                    transactionHash
                );
                instruction = instruction(instructionId);
            } else if (!hashMatches || instruction.getStatus() != TransferStatus.TRANSACTION_PENDING) {
                holdUncertainTransaction(instruction, transactionOutcome);
                return output("reconciliationMatched", false);
            }
            Map<String, Object> output = output("reconciliationMatched", false);
            output.put("reconciliationPending", true);
            return output;
        }
        if (!"CONFIRMED".equals(transactionOutcome) || !finalized || blank(transactionHash)) {
            holdUncertainTransaction(instruction, transactionOutcome);
            Map<String, Object> held = output("reconciliationMatched", false);
            held.put("investigationRequired", true);
            held.put("reconciliationReasonCode", "REVERTED".equals(transactionOutcome)
                ? "CHAIN_TRANSACTION_REVERTED"
                : "CHAIN_OUTCOME_UNCERTAIN");
            return held;
        }

        String hash = transactionHash;
        boolean transactionMatches = !blank(instruction.getTransactionHash())
            && instruction.getTransactionHash().equalsIgnoreCase(hash);
        instruction = markConfirmed(instructionId, instruction);
        transition(
            instruction,
            TransferStatus.TRANSACTION_CONFIRMED,
            TransferStatus.RECONCILIATION_PENDING,
            null,
            hash
        );
        instruction = instruction(instructionId);
        TransferReconciliationPort.ReconciliationDecision decision;
        try {
            decision = transactionMatches
                ? reconciliationPort.reconcile(instruction, hash)
                : new TransferReconciliationPort.ReconciliationDecision(false, "TRANSACTION_HASH_MISMATCH");
        } catch (RuntimeException chainDataUnavailable) {
            decision = new TransferReconciliationPort.ReconciliationDecision(
                false, "AUTHORITATIVE_CHAIN_DATA_UNAVAILABLE"
            );
        }
        if (decision == null || !decision.matched()) {
            String reason = decision == null || blank(decision.reasonCode())
                ? "RECONCILIATION_MISMATCH"
                : decision.reasonCode();
            transition(
                instruction,
                TransferStatus.RECONCILIATION_PENDING,
                TransferStatus.RECONCILIATION_MISMATCH,
                reason,
                hash
            );
            Map<String, Object> output = output("reconciliationMatched", false);
            output.put("reconciliationReasonCode", reason);
            output.put("investigationRequired", true);
            return output;
        }
        transition(instruction, TransferStatus.RECONCILIATION_PENDING, TransferStatus.COMPLETED, null, hash);
        return output("reconciliationMatched", true);
    }

    private Map<String, Object> replayTerminalReconciliation(TransferInstruction instruction, String transactionHash) {
        boolean sameTransaction = !blank(transactionHash) && !blank(instruction.getTransactionHash())
            && instruction.getTransactionHash().equalsIgnoreCase(transactionHash);
        if (instruction.getStatus() == TransferStatus.COMPLETED && sameTransaction) {
            Map<String, Object> output = output("reconciliationMatched", true);
            output.put("reconciliationReplay", true);
            return output;
        }
        if (instruction.getStatus() == TransferStatus.RECONCILIATION_MISMATCH) {
            Map<String, Object> output = output("reconciliationMatched", false);
            output.put("reconciliationReasonCode", instruction.getReasonCode());
            output.put("investigationRequired", true);
            output.put("reconciliationReplay", true);
            return output;
        }
        return null;
    }

    private void recordComplianceEvaluation(
        TransferInstruction instruction,
        Map<ScreeningType, List<ScreeningDecision>> decisions,
        Set<ScreeningType> requiredTypes,
        ComplianceOutcome outcome,
        boolean holdPersisted
    ) {
        if (instruction.getStatus() != TransferStatus.SCREENING_PENDING) {
            return;
        }
        Set<String> policyVersions = new java.util.TreeSet<>();
        for (ScreeningType type : requiredTypes) {
            for (ScreeningRequestRecord request : requestRecords(instruction.getInstructionId(), type)) {
                if (request.getPolicyVersion() != null) {
                    policyVersions.add(type + "=" + request.getPolicyVersion());
                }
            }
        }
        StringBuilder summary = new StringBuilder("outcome=").append(outcome);
        summary.append(";required=").append(requiredTypes);
        summary.append(";decisions=").append(new java.util.TreeMap<>(decisions));
        summary.append(";policyVersions=").append(policyVersions);
        summary.append(";unresolvedOrInconsistent=").append(holdPersisted);
        instruction.setComplianceEvaluation(summary.toString());
        if (outcome == ComplianceOutcome.APPROVED) {
            instruction.setApprovedFingerprint(approvalFingerprint(instruction));
        }
        instructions.save(instruction);
    }
    private void persistComplianceOutcome(TransferInstruction instruction, ComplianceOutcome outcome) {
        TransferStatus target = switch (outcome) {
            case APPROVED -> TransferStatus.COMPLIANCE_APPROVED;
            case REJECTED -> TransferStatus.COMPLIANCE_REJECTED;
            case MANUAL_REVIEW -> TransferStatus.MANUAL_REVIEW;
            case RESTRICTED, HOLD, WAITING -> TransferStatus.COMPLIANCE_HOLD;
        };
        if (instruction.getStatus() == target) {
            return;
        }
        if (instruction.getStatus() == TransferStatus.SCREENING_PENDING) {
            String reason = switch (outcome) {
                case HOLD, WAITING -> "MANDATORY_SCREENING_UNRESOLVED";
                case RESTRICTED -> "SANCTIONS_RESTRICTION";
                default -> null;
            };
            transition(instruction, TransferStatus.SCREENING_PENDING, target, reason, null);
        }
    }

    private boolean allMandatoryApproved(String instructionId) {
        Set<ScreeningType> requiredTypes = EnumSet.allOf(ScreeningType.class);
        for (ScreeningType type : ScreeningType.values()) {
            List<ScreeningRequestRecord> typeRequests = requestRecords(instructionId, type);
            if (type == ScreeningType.KYC && typeRequests.size() == 1
                && ScreeningRequestStatus.NOT_REQUIRED.name().equals(typeRequests.get(0).getPublicationStatus())) {
                requiredTypes.remove(ScreeningType.KYC);
                continue;
            }
            if (typeRequests.isEmpty()) {
                return false;
            }
            List<ScreeningResultRecord> typeResults = resultRecords(instructionId, type);
            if (type == ScreeningType.KYC) {
                Integer expectedCount = typeRequests.get(0).getRequiredSubjectCount();
                if (expectedCount == null || expectedCount != typeRequests.size()
                    || typeRequests.stream().anyMatch(request ->
                        !expectedCount.equals(request.getRequiredSubjectCount())
                            || request.getSubjectReference() == null)) {
                    return false;
                }
            } else if (typeRequests.size() != 1) {
                return false;
            }
            Map<String, ScreeningResultRecord> resultByRequestId = new HashMap<>();
            for (ScreeningResultRecord result : typeResults) {
                resultByRequestId.put(result.getRequestId(), result);
            }
            if (typeResults.size() != typeRequests.size()) {
                return false;
            }
            for (ScreeningRequestRecord request : typeRequests) {
                ScreeningResultRecord result = resultByRequestId.get(request.getRequestId());
                if (result == null || decision(result) != ScreeningDecision.APPROVED
                    || !ScreeningRequestStatus.PUBLISHED.name().equals(request.getPublicationStatus())) {
                    return false;
                }
            }
        }
        return !requiredTypes.isEmpty();
    }

    private List<ScreeningRequestRecord> requestRecords(String instructionId, ScreeningType type) {
        if (type == ScreeningType.KYC) {
            return requests.findAllByInstructionIdAndScreeningType(instructionId, type.name());
        }
        return requests.findByInstructionIdAndScreeningType(instructionId, type.name())
            .map(List::of)
            .orElseGet(List::of);
    }

    private List<ScreeningResultRecord> resultRecords(String instructionId, ScreeningType type) {
        if (type == ScreeningType.KYC) {
            return results.findAllByInstructionIdAndScreeningType(instructionId, type.name());
        }
        return results.findByInstructionIdAndScreeningType(instructionId, type.name())
            .map(List::of)
            .orElseGet(List::of);
    }

    private boolean safePolicyCheck(TransferInstruction instruction) {
        try {
            return preExecutionPolicy.isEligible(instruction);
        } catch (RuntimeException policyUnavailable) {
            return false;
        }
    }

    private void holdUncertainTransaction(TransferInstruction instruction, String outcome) {
        if (instruction.getStatus() == TransferStatus.SUBMISSION_IN_PROGRESS) {
            transition(
                instruction,
                TransferStatus.SUBMISSION_IN_PROGRESS,
                TransferStatus.TRANSACTION_UNKNOWN,
                "CHAIN_OUTCOME_UNCERTAIN",
                instruction.getTransactionHash()
            );
            return;
        }
        if (instruction.getStatus() == TransferStatus.TRANSACTION_SUBMITTED) {
            transition(
                instruction,
                TransferStatus.TRANSACTION_SUBMITTED,
                TransferStatus.TRANSACTION_PENDING,
                null,
                instruction.getTransactionHash()
            );
            instruction = instruction(instruction.getInstructionId());
        }
        if (instruction.getStatus() == TransferStatus.TRANSACTION_PENDING) {
            String reason = "REVERTED".equals(outcome) ? "CHAIN_TRANSACTION_REVERTED" : "CHAIN_OUTCOME_UNCERTAIN";
            TransferStatus target = "REVERTED".equals(outcome)
                ? TransferStatus.TRANSACTION_REVERTED
                : TransferStatus.TRANSACTION_UNKNOWN;
            transition(instruction, TransferStatus.TRANSACTION_PENDING, target, reason, instruction.getTransactionHash());
        }
    }

    private TransferInstruction markConfirmed(String instructionId, TransferInstruction instruction) {
        if (instruction.getStatus() == TransferStatus.TRANSACTION_SUBMITTED) {
            transition(
                instruction,
                TransferStatus.TRANSACTION_SUBMITTED,
                TransferStatus.TRANSACTION_PENDING,
                null,
                instruction.getTransactionHash()
            );
            instruction = instruction(instructionId);
        }
        if (instruction.getStatus() == TransferStatus.TRANSACTION_PENDING) {
            transition(
                instruction,
                TransferStatus.TRANSACTION_PENDING,
                TransferStatus.TRANSACTION_CONFIRMED,
                null,
                instruction.getTransactionHash()
            );
            instruction = instruction(instructionId);
        }
        return instruction;
    }

    private TransferInstruction transition(
        TransferInstruction instruction,
        TransferStatus expected,
        TransferStatus next,
        String reasonCode,
        String transactionHash
    ) {
        if (instruction.getStatus() == next) {
            return instruction;
        }
        try {
            return lifecycle.transition(
                instruction.getInstructionId(), expected, next, reasonCode, transactionHash
            );
        } catch (ResponseStatusException conflict) {
            TransferInstruction current = instruction(instruction.getInstructionId());
            if (current.getStatus() == next) {
                return current;
            }
            throw conflict;
        }
    }

    private TransferInstruction instruction(String id) {
        if (blank(id)) {
            throw new IllegalArgumentException("Transfer instruction ID is required");
        }
        return instructions.findById(id)
            .orElseThrow(() -> new IllegalStateException("Transfer instruction is not persisted"));
    }

    private ScreeningDecision effectiveDecision(ScreeningRequestRecord request, ScreeningResultRecord result) {
        ScreeningDecision actual = decision(result);
        if (ScreeningType.valueOf(request.getScreeningType()) != ScreeningType.SANCTIONS
            && request.getAcceptableDecisions().contains(actual.name())
            && actual != ScreeningDecision.REJECTED
            && actual != ScreeningDecision.ERROR
            && actual != ScreeningDecision.RESTRICTED) {
            return ScreeningDecision.APPROVED;
        }
        return actual;
    }

    private ScreeningDecision decision(ScreeningResultRecord result) {
        try {
            return ScreeningDecision.valueOf(result.getDecision());
        } catch (RuntimeException invalid) {
            return ScreeningDecision.ERROR;
        }
    }

    private boolean validSubmission(TransferInstruction instruction, TransferSubmission submission) {
        if (submission == null || submission.outcome() == null
            || !instruction.getInstructionId().equals(submission.instructionId())
            || instruction.getNetworkId() != submission.networkId()
            || !same(instruction.getTokenIdentifier(), submission.tokenContract())
            || !same(instruction.getSourceWallet(), submission.source())
            || !same(instruction.getDestinationWallet(), submission.destination())
            || blank(instruction.getAmount()) || blank(submission.amount())) {
            return false;
        }
        try {
            return new java.math.BigDecimal(instruction.getAmount())
                .compareTo(new java.math.BigDecimal(submission.amount())) == 0;
        } catch (NumberFormatException invalidAmount) {
            return false;
        }
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private Map<String, Object> rejectedValidation(String reasonCode) {
        Map<String, Object> output = output("validationPassed", false);
        output.put("validationReasonCode", reasonCode);
        return output;
    }

    private Map<String, Object> screeningRequestOutput(
        ScreeningType type,
        boolean ready,
        String requestId,
        String reasonCode
    ) {
        Map<String, Object> output = output(requestReadyVariable(type), ready);
        if (requestId != null) {
            output.put(requestIdVariable(type), requestId);
        }
        if (reasonCode != null) {
            output.put("screeningReasonCode", reasonCode);
        }
        return output;
    }

    private Map<String, Object> submissionOutput(String outcome, String hash, String reasonCode) {
        Map<String, Object> output = output("submissionOutcome", outcome);
        if (hash != null) {
            output.put("transactionHash", hash);
        }
        if (reasonCode != null) {
            output.put("submissionReasonCode", reasonCode);
        }
        return output;
    }

    private Map<String, Object> cancellationOutput(String outcome, String reasonCode) {
        Map<String, Object> output = output("cancellationOutcome", outcome);
        if (reasonCode != null) {
            output.put("cancellationReasonCode", reasonCode);
        }
        return output;
    }

    private boolean cancellableBeforeSubmission(TransferStatus status) {
        return switch (status) {
            case RECEIVED, WORKFLOW_START_UNKNOWN, VALIDATING, VALIDATION_FAILED, SCREENING_PENDING,
                COMPLIANCE_APPROVED, COMPLIANCE_REJECTED, MANUAL_REVIEW, COMPLIANCE_HOLD,
                READY_FOR_EXECUTION -> true;
            default -> false;
        };
    }

    private boolean submissionStarted(TransferStatus status) {
        return switch (status) {
            case SUBMISSION_IN_PROGRESS, TRANSACTION_SUBMITTED, TRANSACTION_PENDING, TRANSACTION_REVERTED,
                TRANSACTION_UNKNOWN, TRANSACTION_CONFIRMED, RECONCILIATION_PENDING, RECONCILIATION_MISMATCH,
                COMPLETED -> true;
            default -> false;
        };
    }

    private Map<String, Object> output(String key, Object value) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put(key, value);
        return output;
    }

    private String requestReadyVariable(ScreeningType type) {
        return type.name().toLowerCase(java.util.Locale.ROOT) + "RequestReady";
    }

    private String requestIdVariable(ScreeningType type) {
        return type.name().toLowerCase(java.util.Locale.ROOT) + "ScreeningRequestId";
    }

    private String timeoutVariable(ScreeningType type) {
        return type.name().toLowerCase(java.util.Locale.ROOT) + "TimeoutPersisted";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
