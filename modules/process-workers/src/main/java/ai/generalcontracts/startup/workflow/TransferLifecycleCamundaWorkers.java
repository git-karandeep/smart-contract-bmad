package ai.generalcontracts.startup.workflow;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TransferLifecycleCamundaWorkers {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransferLifecycleCamundaWorkers.class);
    private final TransferLifecycleJobHandlers handlers;

    public TransferLifecycleCamundaWorkers(TransferLifecycleJobHandlers handlers) {
        this.handlers = handlers;
    }

    @JobWorker(type = "transfer-validate", autoComplete = false)
    public void validate(JobClient client, ActivatedJob job) {
        execute(client, job, () -> handlers.validate(string(job, "instructionId")));
    }

    @JobWorker(type = "screening-request-kyc", autoComplete = false)
    public void requestKyc(JobClient client, ActivatedJob job) {
        execute(client, job, () -> handlers.requestScreening(string(job, "instructionId"), ScreeningType.KYC));
    }

    @JobWorker(type = "screening-request-aml", autoComplete = false)
    public void requestAml(JobClient client, ActivatedJob job) {
        execute(client, job, () -> handlers.requestScreening(string(job, "instructionId"), ScreeningType.AML));
    }

    @JobWorker(type = "screening-request-sanctions", autoComplete = false)
    public void requestSanctions(JobClient client, ActivatedJob job) {
        execute(client, job, () -> handlers.requestScreening(string(job, "instructionId"), ScreeningType.SANCTIONS));
    }

    @JobWorker(type = "screening-timeout-kyc", autoComplete = false)
    public void timeoutKyc(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            String requestId = string(job, "screeningRequestId");
            return requestId == null
            ? handlers.recordScreeningTimeout(string(job, "instructionId"), ScreeningType.KYC)
            : handlers.recordScreeningTimeout(
                string(job, "instructionId"), ScreeningType.KYC, requestId
            );
        });
    }

    @JobWorker(type = "screening-timeout-aml", autoComplete = false)
    public void timeoutAml(JobClient client, ActivatedJob job) {
        execute(client, job,
            () -> handlers.recordScreeningTimeout(string(job, "instructionId"), ScreeningType.AML));
    }

    @JobWorker(type = "screening-timeout-sanctions", autoComplete = false)
    public void timeoutSanctions(JobClient client, ActivatedJob job) {
        execute(client, job,
            () -> handlers.recordScreeningTimeout(string(job, "instructionId"), ScreeningType.SANCTIONS));
    }

    @JobWorker(type = "compliance-aggregate", autoComplete = false)
    public void aggregate(JobClient client, ActivatedJob job) {
        execute(client, job, () -> handlers.aggregateCompliance(string(job, "instructionId")));
    }

    @JobWorker(type = "transfer-pre-execution-gate", autoComplete = false)
    public void preExecutionGate(JobClient client, ActivatedJob job) {
        execute(client, job, () -> handlers.preExecutionGate(
            string(job, "instructionId"),
            string(job, "complianceOutcome")
        ));
    }

    @JobWorker(type = "transfer-submit", autoComplete = false)
    public void submit(JobClient client, ActivatedJob job) {
        execute(client, job, () -> handlers.submit(
            string(job, "instructionId"),
            Boolean.TRUE.equals(job.getVariablesAsMap().get("executionAllowed"))
        ));
    }

    @JobWorker(type = "transfer-recover-unknown", autoComplete = false)
    public void recoverUnknownSubmission(JobClient client, ActivatedJob job) {
        execute(client, job, () -> handlers.recoverUnknownSubmission(string(job, "instructionId")));
    }

    @JobWorker(type = "transfer-reconcile", autoComplete = false)
    public void reconcile(JobClient client, ActivatedJob job) {
        execute(client, job, () -> {
            Map<String, Object> variables = job.getVariablesAsMap();
            return handlers.reconcile(
                string(variables, "instructionId"),
                string(variables, "transactionHash"),
                string(variables, "transactionOutcome"),
                Boolean.TRUE.equals(variables.get("finalized"))
            );
        });
    }

    @JobWorker(type = "transfer-cancel", autoComplete = false)
    public void cancel(JobClient client, ActivatedJob job) {
        execute(client, job, () -> handlers.cancel(string(job, "instructionId")));
    }

    private void execute(JobClient client, ActivatedJob job, Supplier<Map<String, Object>> action) {
        try {
            Map<String, Object> variables = action.get();
            client.newCompleteCommand(job.getKey()).variables(variables).send().join();
        } catch (RuntimeException technicalFailure) {
            int remainingRetries = Math.max(0, job.getRetries() - 1);
            LOGGER.error(
                "Camunda worker execution failed: type={}, jobKey={}, retriesRemaining={}, exceptionType={}",
                job.getType(),
                job.getKey(),
                remainingRetries,
                technicalFailure.getClass().getName()
            );
            client.newFailCommand(job.getKey())
                .retries(remainingRetries)
                .retryBackoff(retryBackoff(job.getRetries()))
                .errorMessage("Transfer worker execution failed; inspect worker logs for diagnostics")
                .send()
                .join();
        }
    }

    static Duration retryBackoff(int currentRetries) {
        int failedAttempt = Math.max(1, 4 - currentRetries);
        long seconds = Math.min(4, 1L << Math.min(2, failedAttempt - 1));
        return Duration.ofSeconds(seconds);
    }

    private String string(ActivatedJob job, String name) {
        return string(job.getVariablesAsMap(), name);
    }

    private String string(Map<String, Object> variables, String name) {
        Object value = variables.get(name);
        return value instanceof String text ? text : null;
    }
}
