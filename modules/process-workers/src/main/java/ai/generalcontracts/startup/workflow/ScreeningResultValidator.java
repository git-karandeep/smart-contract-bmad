package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.startup.transfer.ScreeningRequestRecord;

public final class ScreeningResultValidator {
    public void validate(ScreeningResult result, ScreeningRequestRecord expected) {
        if (result == null || expected == null || blank(result.providerId()) || blank(result.messageId())
            || blank(result.instructionId()) || blank(result.screeningRequestId()) || blank(result.correlationId())
            || result.screeningType() == null || result.decision() == null || result.screenedAt() == null) {
            throw new IllegalArgumentException("Screening result is incomplete");
        }
        if (!expected.getRequestId().equals(result.screeningRequestId())
            || !expected.getInstructionId().equals(result.instructionId())
            || !expected.getCorrelationId().equals(result.correlationId())
            || !expected.getProviderId().equals(result.providerId())
            || !expected.getScreeningType().equals(result.screeningType().name())) {
            throw new IllegalArgumentException("Screening result correlation does not match its request");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
