package ai.generalcontracts.startup.workflow;

import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class ScreeningDecisionAggregator {
    private static final Set<ScreeningType> MANDATORY = Set.of(
        ScreeningType.KYC, ScreeningType.AML, ScreeningType.SANCTIONS
    );

    public ComplianceOutcome evaluate(Map<ScreeningType, ScreeningDecision> decisions, boolean timedOut) {
        EnumMapBuilder grouped = new EnumMapBuilder();
        if (decisions != null) {
            decisions.forEach((type, decision) -> grouped.add(type, decision));
        }
        return evaluate(grouped.values(), MANDATORY, timedOut);
    }

    public ComplianceOutcome evaluate(
        Map<ScreeningType, List<ScreeningDecision>> decisions,
        Set<ScreeningType> requiredTypes,
        boolean timedOut
    ) {
        if (decisions == null || decisions.isEmpty() || requiredTypes == null || requiredTypes.isEmpty()) {
            return timedOut ? ComplianceOutcome.HOLD : ComplianceOutcome.WAITING;
        }
        if (requiredTypes.contains(ScreeningType.SANCTIONS)
            && decisions.getOrDefault(ScreeningType.SANCTIONS, List.of()).contains(ScreeningDecision.RESTRICTED)) {
            return ComplianceOutcome.RESTRICTED;
        }
        if (requiredTypes.stream().map(type -> decisions.getOrDefault(type, List.of()))
            .anyMatch(outcomes -> outcomes.contains(ScreeningDecision.REJECTED))) {
            return ComplianceOutcome.REJECTED;
        }
        if (requiredTypes.stream().map(type -> decisions.getOrDefault(type, List.of()))
            .anyMatch(outcomes -> outcomes.contains(ScreeningDecision.REVIEW_REQUIRED))) {
            return ComplianceOutcome.MANUAL_REVIEW;
        }
        if (requiredTypes.stream().map(type -> decisions.getOrDefault(type, List.of()))
            .anyMatch(outcomes -> outcomes.contains(ScreeningDecision.ERROR)) || timedOut) {
            return ComplianceOutcome.HOLD;
        }
        if (!decisions.keySet().containsAll(requiredTypes)
            || requiredTypes.stream().anyMatch(type -> decisions.get(type) == null || decisions.get(type).isEmpty())) {
            return ComplianceOutcome.WAITING;
        }
        return requiredTypes.stream().allMatch(type ->
            decisions.get(type).stream().allMatch(decision -> decision == ScreeningDecision.APPROVED))
            ? ComplianceOutcome.APPROVED
            : ComplianceOutcome.HOLD;
    }

    private static final class EnumMapBuilder {
        private final java.util.EnumMap<ScreeningType, List<ScreeningDecision>> values =
            new java.util.EnumMap<>(ScreeningType.class);

        private void add(ScreeningType type, ScreeningDecision decision) {
            if (type != null && decision != null) {
                values.computeIfAbsent(type, ignored -> new ArrayList<>()).add(decision);
            }
        }

        private Map<ScreeningType, List<ScreeningDecision>> values() {
            return values;
        }
    }
}
