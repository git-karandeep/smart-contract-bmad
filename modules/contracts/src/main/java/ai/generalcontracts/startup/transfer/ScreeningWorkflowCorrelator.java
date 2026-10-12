package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.startup.workflow.ScreeningResult;

public interface ScreeningWorkflowCorrelator {
    void correlate(ScreeningResult result);
}
