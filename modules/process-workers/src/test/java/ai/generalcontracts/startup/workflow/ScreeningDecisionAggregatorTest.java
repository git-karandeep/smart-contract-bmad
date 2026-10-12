package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScreeningDecisionAggregatorTest {
    private final ScreeningDecisionAggregator aggregator = new ScreeningDecisionAggregator();

    @Test
    void approvesOnlyWhenEveryMandatoryScreeningApproves() {
        assertEquals(ComplianceOutcome.APPROVED, aggregator.evaluate(Map.of(
            ScreeningType.KYC, ScreeningDecision.APPROVED,
            ScreeningType.AML, ScreeningDecision.APPROVED,
            ScreeningType.SANCTIONS, ScreeningDecision.APPROVED
        ), false));
    }

    @Test
    void waitsForMissingMandatoryResultsAndHoldsOnTimeout() {
        Map<ScreeningType, ScreeningDecision> partial = Map.of(
            ScreeningType.KYC, ScreeningDecision.APPROVED,
            ScreeningType.AML, ScreeningDecision.APPROVED
        );
        assertEquals(ComplianceOutcome.WAITING, aggregator.evaluate(partial, false));
        assertEquals(ComplianceOutcome.HOLD, aggregator.evaluate(partial, true));
    }

    @Test
    void neverConvertsAnyNonApprovalIntoApproval() {
        for (ScreeningDecision outcome : new ScreeningDecision[] {
            ScreeningDecision.REJECTED, ScreeningDecision.REVIEW_REQUIRED,
            ScreeningDecision.ERROR, ScreeningDecision.RESTRICTED
        }) {
            EnumMap<ScreeningType, ScreeningDecision> decisions = new EnumMap<>(ScreeningType.class);
            decisions.put(ScreeningType.KYC, outcome);
            decisions.put(ScreeningType.AML, ScreeningDecision.APPROVED);
            decisions.put(ScreeningType.SANCTIONS, ScreeningDecision.APPROVED);
            assertEquals(
                outcome == ScreeningDecision.REJECTED ? ComplianceOutcome.REJECTED
                    : outcome == ScreeningDecision.REVIEW_REQUIRED ? ComplianceOutcome.MANUAL_REVIEW
                    : ComplianceOutcome.HOLD,
                aggregator.evaluate(decisions, false)
            );
        }
    }

    @Test
    void preservesSanctionsRestrictionAsDistinctOutcome() {
        assertEquals(ComplianceOutcome.RESTRICTED, aggregator.evaluate(Map.of(
            ScreeningType.KYC, ScreeningDecision.APPROVED,
            ScreeningType.AML, ScreeningDecision.APPROVED,
            ScreeningType.SANCTIONS, ScreeningDecision.RESTRICTED
        ), false));
    }
}
