package ai.generalcontracts.startup.workflow;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class ScreeningDecisionAggregator {
    private static final Set<ScreeningType> MANDATORY = Set.of(
        ScreeningType.KYC, ScreeningType.AML, ScreeningType.SANCTIONS
    );

    public ComplianceOutcome evaluate(Map<ScreeningType, ScreeningDecision> decisions, boolean timedOut) {
        if (decisions == null || decisions.isEmpty()) {
            return timedOut ? ComplianceOutcome.HOLD : ComplianceOutcome.WAITING;
        }
        if (decisions.get(ScreeningType.SANCTIONS) == ScreeningDecision.RESTRICTED) {
            return ComplianceOutcome.RESTRICTED;
        }
        if (decisions.containsValue(ScreeningDecision.REJECTED)) {
            return ComplianceOutcome.REJECTED;
        }
        if (decisions.containsValue(ScreeningDecision.REVIEW_REQUIRED)) {
            return ComplianceOutcome.MANUAL_REVIEW;
        }
        if (decisions.containsValue(ScreeningDecision.ERROR) || timedOut) {
            return ComplianceOutcome.HOLD;
        }
        if (!decisions.keySet().containsAll(MANDATORY)) {
            return ComplianceOutcome.WAITING;
        }
        return MANDATORY.stream().allMatch(type -> decisions.get(type) == ScreeningDecision.APPROVED)
            ? ComplianceOutcome.APPROVED
            : ComplianceOutcome.HOLD;
    }
}
