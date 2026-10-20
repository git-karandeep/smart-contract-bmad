package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransferLifecycleCamundaWorkersTest {
    @Test
    void transientWorkerFailureUsesBoundedExponentialBackoff() {
        TransferLifecycleJobHandlers handlers = mock(TransferLifecycleJobHandlers.class);
        when(handlers.validate("instruction-1")).thenThrow(new IllegalStateException("temporary failure"));
        TransferLifecycleCamundaWorkers workers = new TransferLifecycleCamundaWorkers(handlers);
        ActivatedJob job = job(3);
        JobClient client = mock(JobClient.class);
        FailJobCommandStep1 failCommand = mock(FailJobCommandStep1.class);
        FailJobCommandStep1.FailJobCommandStep2 failWithRetries = mock(
            FailJobCommandStep1.FailJobCommandStep2.class
        );
        when(client.newFailCommand(17L)).thenReturn(failCommand);
        when(failCommand.retries(2)).thenReturn(failWithRetries);
        when(failWithRetries.retryBackoff(Duration.ofSeconds(1))).thenReturn(failWithRetries);
        when(failWithRetries.errorMessage(
            "Transfer worker execution failed; inspect worker logs for diagnostics"
        )).thenReturn(failWithRetries);
        CamundaFuture<FailJobResponse> failResponse = mock(CamundaFuture.class);
        when(failResponse.join()).thenReturn(mock(FailJobResponse.class));
        when(failWithRetries.send()).thenReturn(failResponse);

        workers.validate(client, job);

        verify(client).newFailCommand(17L);
        verify(failCommand).retries(2);
        verify(failWithRetries).retryBackoff(Duration.ofSeconds(1));
        verify(failWithRetries).errorMessage(
            "Transfer worker execution failed; inspect worker logs for diagnostics"
        );
        verify(client, never()).newCompleteCommand(17L);
    }

    @Test
    void businessRejectionCompletesNormallyInsteadOfSchedulingTechnicalRetry() {
        TransferLifecycleJobHandlers handlers = mock(TransferLifecycleJobHandlers.class);
        when(handlers.validate("instruction-1")).thenReturn(Map.of(
            "validationPassed", false,
            "validationReasonCode", "TRANSFER_NOT_ELIGIBLE"
        ));
        TransferLifecycleCamundaWorkers workers = new TransferLifecycleCamundaWorkers(handlers);
        ActivatedJob job = job(3);
        JobClient client = mock(JobClient.class);
        CompleteJobCommandStep1 completeCommand = mock(CompleteJobCommandStep1.class);
        when(client.newCompleteCommand(17L)).thenReturn(completeCommand);
        when(completeCommand.variables(anyMap())).thenReturn(completeCommand);
        CamundaFuture<CompleteJobResponse> completeResponse = mock(CamundaFuture.class);
        when(completeResponse.join()).thenReturn(mock(CompleteJobResponse.class));
        when(completeCommand.send()).thenReturn(completeResponse);

        workers.validate(client, job);

        verify(completeCommand).variables(Map.of(
            "validationPassed", false,
            "validationReasonCode", "TRANSFER_NOT_ELIGIBLE"
        ));
        verify(client, never()).newFailCommand(17L);
    }

    @Test
    void exhaustedServiceTaskRetriesAreReportedToCamundaAsAnIncident() {
        TransferLifecycleJobHandlers handlers = mock(TransferLifecycleJobHandlers.class);
        when(handlers.validate("instruction-1")).thenThrow(new IllegalStateException("permanent failure"));
        TransferLifecycleCamundaWorkers workers = new TransferLifecycleCamundaWorkers(handlers);
        ActivatedJob job = job(1);
        JobClient client = mock(JobClient.class);
        FailJobCommandStep1 failCommand = mock(FailJobCommandStep1.class);
        FailJobCommandStep1.FailJobCommandStep2 failWithRetries = mock(
            FailJobCommandStep1.FailJobCommandStep2.class
        );
        when(client.newFailCommand(17L)).thenReturn(failCommand);
        when(failCommand.retries(0)).thenReturn(failWithRetries);
        when(failWithRetries.retryBackoff(Duration.ofSeconds(4))).thenReturn(failWithRetries);
        when(failWithRetries.errorMessage(
            "Transfer worker execution failed; inspect worker logs for diagnostics"
        )).thenReturn(failWithRetries);
        CamundaFuture<FailJobResponse> failResponse = mock(CamundaFuture.class);
        when(failResponse.join()).thenReturn(mock(FailJobResponse.class));
        when(failWithRetries.send()).thenReturn(failResponse);

        workers.validate(client, job);

        verify(failCommand).retries(0);
        verify(failWithRetries).retryBackoff(Duration.ofSeconds(4));
    }

    @Test
    void retryBackoffCapsAtFourSeconds() {
        assertEquals(Duration.ofSeconds(1), TransferLifecycleCamundaWorkers.retryBackoff(3));
        assertEquals(Duration.ofSeconds(2), TransferLifecycleCamundaWorkers.retryBackoff(2));
        assertEquals(Duration.ofSeconds(4), TransferLifecycleCamundaWorkers.retryBackoff(1));
        assertEquals(Duration.ofSeconds(4), TransferLifecycleCamundaWorkers.retryBackoff(0));
    }

    private ActivatedJob job(int retries) {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(17L);
        when(job.getRetries()).thenReturn(retries);
        when(job.getVariablesAsMap()).thenReturn(Map.of("instructionId", "instruction-1"));
        return job;
    }
}
