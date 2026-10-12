package ai.generalcontracts.startup.workflow;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TransferLifecycleCamundaWorkers {
    private final TransferLifecycleJobHandlers handlers;

    public TransferLifecycleCamundaWorkers(TransferLifecycleJobHandlers handlers) {
        this.handlers = handlers;
    }

    @JobWorker(type = "transfer-validate", autoComplete = false)
    public void validate(JobClient client, ActivatedJob job) {
        complete(client, job, handlers.validate(string(job, "instructionId")));
    }

    @JobWorker(type = "screening-request-kyc", autoComplete = false)
    public void requestKyc(JobClient client, ActivatedJob job) {
        complete(client, job, handlers.requestScreening(string(job, "instructionId"), ScreeningType.KYC));
    }

    @JobWorker(type = "screening-request-aml", autoComplete = false)
    public void requestAml(JobClient client, ActivatedJob job) {
        complete(client, job, handlers.requestScreening(string(job, "instructionId"), ScreeningType.AML));
    }

    @JobWorker(type = "screening-request-sanctions", autoComplete = false)
    public void requestSanctions(JobClient client, ActivatedJob job) {
        complete(client, job, handlers.requestScreening(string(job, "instructionId"), ScreeningType.SANCTIONS));
    }

    @JobWorker(type = "screening-timeout-kyc", autoComplete = false)
    public void timeoutKyc(JobClient client, ActivatedJob job) {
        complete(client, job, handlers.recordScreeningTimeout(string(job, "instructionId"), ScreeningType.KYC));
    }

    @JobWorker(type = "screening-timeout-aml", autoComplete = false)
    public void timeoutAml(JobClient client, ActivatedJob job) {
        complete(client, job, handlers.recordScreeningTimeout(string(job, "instructionId"), ScreeningType.AML));
    }

    @JobWorker(type = "screening-timeout-sanctions", autoComplete = false)
    public void timeoutSanctions(JobClient client, ActivatedJob job) {
        complete(client, job,
            handlers.recordScreeningTimeout(string(job, "instructionId"), ScreeningType.SANCTIONS));
    }

    @JobWorker(type = "compliance-aggregate", autoComplete = false)
    public void aggregate(JobClient client, ActivatedJob job) {
        complete(client, job, handlers.aggregateCompliance(string(job, "instructionId")));
    }

    @JobWorker(type = "transfer-pre-execution-gate", autoComplete = false)
    public void preExecutionGate(JobClient client, ActivatedJob job) {
        complete(client, job, handlers.preExecutionGate(
            string(job, "instructionId"),
            string(job, "complianceOutcome")
        ));
    }

    @JobWorker(type = "transfer-submit", autoComplete = false)
    public void submit(JobClient client, ActivatedJob job) {
        complete(client, job, handlers.submit(
            string(job, "instructionId"),
            Boolean.TRUE.equals(job.getVariablesAsMap().get("executionAllowed"))
        ));
    }

    @JobWorker(type = "transfer-reconcile", autoComplete = false)
    public void reconcile(JobClient client, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        complete(client, job, handlers.reconcile(
            string(variables, "instructionId"),
            string(variables, "transactionHash"),
            string(variables, "transactionOutcome"),
            Boolean.TRUE.equals(variables.get("finalized"))
        ));
    }

    private void complete(JobClient client, ActivatedJob job, Map<String, Object> variables) {
        client.newCompleteCommand(job.getKey()).variables(variables).send().join();
    }

    private String string(ActivatedJob job, String name) {
        return string(job.getVariablesAsMap(), name);
    }

    private String string(Map<String, Object> variables, String name) {
        Object value = variables.get(name);
        return value instanceof String text ? text : null;
    }
}
