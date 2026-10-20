package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class TransferLifecycleCamundaProcessTest {
    private static final String PROCESS_ID = "transfer-lifecycle";
    private static final Duration JOB_ACTIVATION_TIMEOUT = Duration.ofMillis(100);

    private CamundaClient client;
    private CamundaProcessTestContext processTestContext;

    @BeforeEach
    void deployGeneratedProcesses() {
        client.newDeployResourceCommand()
            .addResourceFromClasspath("generated-processes/transfer-lifecycle.bpmn")
            .addResourceFromClasspath("generated-processes/kyc-screening-process.bpmn")
            .addResourceFromClasspath("generated-processes/aml-screening-process.bpmn")
            .addResourceFromClasspath("generated-processes/sanctions-screening-process.bpmn")
            .send()
            .join();
    }

    @Test
    void approvedScreeningsAndConfirmedTransactionCompleteGeneratedProcessSet() throws Exception {
        Transfer transfer = startTransfer();
        completeScreeningRequests(transfer.instructionId());
        publishApprovedScreeningResults(transfer.instructionId());

        completeJob("compliance-aggregate", Map.of("complianceOutcome", "APPROVED"));
        completeJob("transfer-pre-execution-gate", Map.of("executionAllowed", true));
        completeJob("transfer-submit", Map.of(
            "submissionOutcome", "SUBMITTED",
            "transactionHash", "0x0123456789abcdef"
        ));
        publishTransactionResult(transfer.instructionId(), "0x0123456789abcdef");
        completeJob("transfer-reconcile", Map.of("reconciliationMatched", true));

        assertProcessState(transfer.processInstanceKey(), "COMPLETED");
        assertNoJob("transfer-submit");
    }

    @Test
    void screeningRejectionCompletesWithoutReachingExecutionOrSubmission() throws Exception {
        Transfer transfer = startTransfer();
        completeScreeningRequests(transfer.instructionId());
        publishApprovedScreeningResults(transfer.instructionId());

        completeJob("compliance-aggregate", Map.of("complianceOutcome", "REJECTED"));

        assertProcessState(transfer.processInstanceKey(), "COMPLETED");
        assertNoJob("transfer-pre-execution-gate");
        assertNoJob("transfer-submit");
    }

    @Test
    void missingMandatoryScreeningResultKeepsProcessOpenAndCannotReachSubmission() throws Exception {
        Transfer transfer = startTransfer();
        completeScreeningRequests(transfer.instructionId());
        publishMessage("KycScreeningResult", transfer.instructionId() + ":KYC", Map.of());
        publishMessage("AmlScreeningResult", transfer.instructionId() + ":AML", Map.of());

        assertEquals("ACTIVE", processState(transfer.processInstanceKey()));
        assertNoJob("compliance-aggregate");
        assertNoJob("transfer-submit");
    }

    @Test
    void screeningTimeoutsJoinAsHoldWithoutReachingSubmission() throws Exception {
        Transfer transfer = startTransfer();
        completeScreeningRequests(transfer.instructionId());

        processTestContext.increaseTime(Duration.ofMinutes(15));
        completeJob("screening-timeout-kyc", Map.of(
            "kycTimeoutPersisted", true,
            "screeningHoldPersisted", true
        ));
        completeJob("screening-timeout-aml", Map.of("amlTimeoutPersisted", true));
        completeJob("screening-timeout-sanctions", Map.of("sanctionsTimeoutPersisted", true));
        completeJob("compliance-aggregate", Map.of("complianceOutcome", "HOLD"));

        assertProcessState(transfer.processInstanceKey(), "COMPLETED");
        assertNoJob("transfer-submit");
    }

    @Test
    void exhaustedScreeningRequestRetriesCreateIncidentAndBlockPayment() throws Exception {
        Transfer transfer = startTransfer();
        ActivatedJob failedJob = awaitJob("screening-request-aml");
        client.newFailCommand(failedJob.getKey())
            .retries(0)
            .errorMessage("simulated permanent screening request failure")
            .send()
            .join();

        assertTrue(awaitIncident(failedJob.getProcessInstanceKey()));
        assertNotEquals("COMPLETED", processState(transfer.processInstanceKey()));
        assertNoJob("transfer-submit");
    }

    @Test
    void unknownSubmissionRecoveryDoesNotRebroadcast() throws Exception {
        Transfer transfer = startTransfer();
        completeScreeningRequests(transfer.instructionId());
        publishApprovedScreeningResults(transfer.instructionId());
        completeJob("compliance-aggregate", Map.of("complianceOutcome", "APPROVED"));
        completeJob("transfer-pre-execution-gate", Map.of("executionAllowed", true));
        completeJob("transfer-submit", Map.of(
            "submissionOutcome", "UNKNOWN",
            "submissionReasonCode", "CHAIN_SUBMISSION_OUTCOME_UNKNOWN"
        ));
        completeJob("transfer-recover-unknown", Map.of(
            "recoveryHasTransactionHash", false,
            "recoveryRequiresInvestigation", true,
            "recoveryReasonCode", "JOURNALED_TRANSACTION_HASH_UNAVAILABLE"
        ));

        assertProcessState(transfer.processInstanceKey(), "COMPLETED");
        assertNoJob("transfer-submit");
        assertNoJob("transfer-reconcile");
    }

    @Test
    void unknownSubmissionWithJournaledHashReconcilesWithoutResubmission() throws Exception {
        Transfer transfer = startTransfer();
        completeScreeningRequests(transfer.instructionId());
        publishApprovedScreeningResults(transfer.instructionId());
        completeJob("compliance-aggregate", Map.of("complianceOutcome", "APPROVED"));
        completeJob("transfer-pre-execution-gate", Map.of("executionAllowed", true));
        completeJob("transfer-submit", Map.of(
            "submissionOutcome", "UNKNOWN",
            "submissionReasonCode", "CHAIN_SUBMISSION_OUTCOME_UNKNOWN"
        ));
        String transactionHash = "0xfedcba9876543210";
        completeJob("transfer-recover-unknown", Map.of(
            "recoveryHasTransactionHash", true,
            "recoveryRequiresInvestigation", false,
            "recoveryReasonCode", "JOURNALED_TRANSACTION_HASH_FOUND",
            "transactionHash", transactionHash,
            "transactionOutcome", "PENDING",
            "finalized", false
        ));
        assertEquals("ACTIVE", processState(transfer.processInstanceKey()));
        assertNoJob("transfer-submit");

        publishTransactionResult(transfer.instructionId(), transactionHash);
        completeJob("transfer-reconcile", Map.of("reconciliationMatched", true));

        assertProcessState(transfer.processInstanceKey(), "COMPLETED");
        assertNoJob("transfer-submit");
    }

    private Transfer startTransfer() throws Exception {
        long previousKey = latestProcessInstanceKey();
        String instructionId = UUID.randomUUID().toString();
        client.newPublishMessageCommand()
            .messageName("TransferInstructionReceived")
            .correlationKey(instructionId)
            .messageId("transfer-instruction:" + instructionId)
            .variables(Map.of(
                "instructionId", instructionId,
                "amlCorrelationKey", instructionId + ":AML",
                "sanctionsCorrelationKey", instructionId + ":SANCTIONS"
            ))
            .send()
            .join();

        long processInstanceKey = awaitProcessInstanceKey(previousKey);
        ActivatedJob validationJob = awaitJob("transfer-validate");
        assertEquals(processInstanceKey, validationJob.getProcessInstanceKey());
        client.newCompleteCommand(validationJob.getKey())
            .variables(Map.of("validationPassed", true))
            .send()
            .join();
        return new Transfer(instructionId, processInstanceKey);
    }

    private void completeScreeningRequests(String instructionId) throws Exception {
        completeJob("screening-request-kyc", Map.of(
            "kycRequestReady", true,
            "kycNotRequired", false,
            "kycScreeningRequestIds", List.of(instructionId + ":KYC")
        ));
        completeJob("screening-request-aml", Map.of(
            "amlRequestReady", true,
            "amlTimeoutPersisted", false
        ));
        completeJob("screening-request-sanctions", Map.of(
            "sanctionsRequestReady", true,
            "sanctionsTimeoutPersisted", false
        ));
    }

    private void publishApprovedScreeningResults(String instructionId) {
        publishMessage("KycScreeningResult", instructionId + ":KYC", Map.of());
        publishMessage("AmlScreeningResult", instructionId + ":AML", Map.of());
        publishMessage("SanctionsScreeningResult", instructionId + ":SANCTIONS", Map.of());
    }

    private void publishTransactionResult(String instructionId, String transactionHash) {
        publishMessage("TransferTransactionResult", instructionId, Map.of(
            "instructionId", instructionId,
            "transactionHash", transactionHash,
            "transactionOutcome", "CONFIRMED",
            "finalized", true
        ));
    }

    private void publishMessage(String name, String correlationKey, Map<String, Object> variables) {
        client.newPublishMessageCommand()
            .messageName(name)
            .correlationKey(correlationKey)
            .messageId(UUID.randomUUID().toString())
            .variables(variables)
            .send()
            .join();
    }

    private ActivatedJob completeJob(String jobType, Map<String, Object> variables) throws Exception {
        ActivatedJob job = awaitJob(jobType);
        client.newCompleteCommand(job.getKey()).variables(variables).send().join();
        return job;
    }

    private ActivatedJob awaitJob(String jobType) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            List<ActivatedJob> jobs = client.newActivateJobsCommand()
                .jobType(jobType)
                .maxJobsToActivate(1)
                .workerName("epic-03-process-tests")
                .timeout(JOB_ACTIVATION_TIMEOUT)
                .send()
                .join()
                .getJobs();
            if (!jobs.isEmpty()) {
                return jobs.get(0);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for Camunda job type " + jobType);
    }

    private void assertNoJob(String jobType) {
        List<ActivatedJob> jobs = client.newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .workerName("epic-03-process-tests")
            .timeout(JOB_ACTIVATION_TIMEOUT)
            .send()
            .join()
            .getJobs();
        assertTrue(jobs.isEmpty(), "Unexpected active job of type " + jobType);
    }

    private long latestProcessInstanceKey() {
        return client.newProcessInstanceSearchRequest()
            .filter(filter -> filter.processDefinitionId(PROCESS_ID))
            .send()
            .join()
            .items()
            .stream()
            .mapToLong(ProcessInstance::getProcessInstanceKey)
            .max()
            .orElse(0);
    }

    private long awaitProcessInstanceKey(long previousKey) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            long key = latestProcessInstanceKey();
            if (key > previousKey) {
                return key;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for transfer process instance");
    }

    private void assertProcessState(long processInstanceKey, String expectedState) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (expectedState.equals(processState(processInstanceKey))) {
                return;
            }
            Thread.sleep(50);
        }
        assertEquals(expectedState, processState(processInstanceKey));
    }

    private String processState(long processInstanceKey) {
        return client.newProcessInstanceGetRequest(processInstanceKey).send().join().getState().name();
    }

    private boolean awaitIncident(long processInstanceKey) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            boolean hasIncident = !client.newProcessInstanceSearchRequest()
                .filter(filter -> filter.processInstanceKey(processInstanceKey).hasIncident(true))
                .send()
                .join()
                .items()
                .isEmpty();
            if (hasIncident) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private record Transfer(String instructionId, long processInstanceKey) {
    }
}
