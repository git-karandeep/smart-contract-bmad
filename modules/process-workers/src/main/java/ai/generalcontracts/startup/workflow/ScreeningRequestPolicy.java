package ai.generalcontracts.startup.workflow;

public record ScreeningRequestPolicy(String providerId, String screeningVersion) {
    public ScreeningRequestPolicy {
        if (providerId == null || providerId.isBlank()
            || screeningVersion == null || screeningVersion.isBlank()) {
            throw new IllegalArgumentException("Configured screening provider and version are required");
        }
    }
}
