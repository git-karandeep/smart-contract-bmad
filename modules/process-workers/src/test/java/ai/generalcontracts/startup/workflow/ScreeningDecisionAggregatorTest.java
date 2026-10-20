package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
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

    @Test
    void requiresEveryPolicyRequiredKycSubjectApprovalAndPreservesItsDecision() {
        EnumMap<ScreeningType, List<ScreeningDecision>> decisions =
            new EnumMap<>(ScreeningType.class);
        decisions.put(ScreeningType.KYC, List.of(
            ScreeningDecision.APPROVED, ScreeningDecision.APPROVED
        ));
        decisions.put(ScreeningType.AML, List.of(ScreeningDecision.APPROVED));
        decisions.put(ScreeningType.SANCTIONS, List.of(ScreeningDecision.APPROVED));
        assertEquals(
            ComplianceOutcome.APPROVED,
            aggregator.evaluate(decisions, EnumSet.allOf(ScreeningType.class), false)
        );
        assertEquals(
            ComplianceOutcome.WAITING,
            aggregator.evaluate(Map.of(
                ScreeningType.AML, List.of(ScreeningDecision.APPROVED),
                ScreeningType.SANCTIONS, List.of(ScreeningDecision.APPROVED)
            ), EnumSet.allOf(ScreeningType.class), false)
        );

        Map<ScreeningDecision, ComplianceOutcome> nonApprovals = Map.of(
            ScreeningDecision.REJECTED, ComplianceOutcome.REJECTED,
            ScreeningDecision.REVIEW_REQUIRED, ComplianceOutcome.MANUAL_REVIEW,
            ScreeningDecision.ERROR, ComplianceOutcome.HOLD
        );
        nonApprovals.forEach((decision, expected) -> {
            decisions.put(ScreeningType.KYC, List.of(ScreeningDecision.APPROVED, decision));
            assertEquals(expected, aggregator.evaluate(
                decisions, EnumSet.allOf(ScreeningType.class), false
            ));
        });
    }

    @Test
    void permitsExplicitPolicyToOmitKycButStillRequiresOtherMandatoryApprovals() {
        assertEquals(
            ComplianceOutcome.APPROVED,
            aggregator.evaluate(Map.of(
                ScreeningType.AML, List.of(ScreeningDecision.APPROVED),
                ScreeningType.SANCTIONS, List.of(ScreeningDecision.APPROVED)
            ), EnumSet.of(ScreeningType.AML, ScreeningType.SANCTIONS), false)
        );
        assertEquals(
            ComplianceOutcome.HOLD,
            aggregator.evaluate(Map.of(
                ScreeningType.AML, List.of(ScreeningDecision.APPROVED),
                ScreeningType.SANCTIONS, List.of(ScreeningDecision.ERROR)
            ), EnumSet.of(ScreeningType.AML, ScreeningType.SANCTIONS), false)
        );
    }

    @Test
    void sanctionsOutcomesStayDistinct() {
        for (Object[] row : new Object[][] {
            {ScreeningDecision.REJECTED, ComplianceOutcome.REJECTED},
            {ScreeningDecision.REVIEW_REQUIRED, ComplianceOutcome.MANUAL_REVIEW},
            {ScreeningDecision.ERROR, ComplianceOutcome.HOLD},
            {ScreeningDecision.RESTRICTED, ComplianceOutcome.RESTRICTED}
        }) {
            assertEquals(row[1], aggregator.evaluate(Map.of(
                ScreeningType.KYC, ScreeningDecision.APPROVED,
                ScreeningType.AML, ScreeningDecision.APPROVED,
                ScreeningType.SANCTIONS, (ScreeningDecision) row[0]
            ), false));
        }
    }

    @Test
    void arrivalOrderAndDuplicateDeliveryDoNotChangeTheOutcome() {
        Map<ScreeningType, List<ScreeningDecision>> late = new EnumMap<>(ScreeningType.class);
        late.put(ScreeningType.SANCTIONS, List.of(ScreeningDecision.APPROVED));
        late.put(ScreeningType.KYC, List.of(ScreeningDecision.APPROVED, ScreeningDecision.APPROVED));
        assertEquals(ComplianceOutcome.WAITING, aggregator.evaluate(late, EnumSet.allOf(ScreeningType.class), false));
        late.put(ScreeningType.AML, List.of(ScreeningDecision.APPROVED));
        assertEquals(ComplianceOutcome.APPROVED, aggregator.evaluate(late, EnumSet.allOf(ScreeningType.class), false));
        assertEquals(ComplianceOutcome.HOLD, aggregator.evaluate(late, EnumSet.allOf(ScreeningType.class), true));
    }
}