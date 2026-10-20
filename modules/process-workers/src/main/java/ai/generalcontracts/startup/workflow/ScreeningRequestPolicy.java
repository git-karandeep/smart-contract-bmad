package ai.generalcontracts.startup.workflow;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public record ScreeningRequestPolicy(
    String providerId,
    String screeningVersion,
    String policyVersion,
    Map<String, String> authorizedReferences,
    Set<ScreeningDecision> acceptableDecisions
) {
    private static final Set<ScreeningDecision> NEVER_ACCEPTABLE =
        EnumSet.of(ScreeningDecision.REJECTED, ScreeningDecision.ERROR, ScreeningDecision.RESTRICTED);

    public ScreeningRequestPolicy {
        if (providerId == null || providerId.isBlank()
            || screeningVersion == null || screeningVersion.isBlank()) {
            throw new IllegalArgumentException("Configured screening provider and version are required");
        }
        authorizedReferences = authorizedReferences == null ? null : Map.copyOf(authorizedReferences);
        acceptableDecisions = acceptableDecisions == null || acceptableDecisions.isEmpty()
            ? Set.of(ScreeningDecision.APPROVED)
            : Set.copyOf(acceptableDecisions);
        if (acceptableDecisions.stream().anyMatch(NEVER_ACCEPTABLE::contains)) {
            throw new IllegalArgumentException("Rejected, restricted, or erroneous results cannot be acceptable");
        }
    }

    public ScreeningRequestPolicy(String providerId, String screeningVersion) {
        this(providerId, screeningVersion, null, null, null);
    }
}
