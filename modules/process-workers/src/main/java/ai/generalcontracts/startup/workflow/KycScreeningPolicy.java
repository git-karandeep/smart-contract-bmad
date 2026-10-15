package ai.generalcontracts.startup.workflow;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record KycScreeningPolicy(
    Requirement requirement,
    String policyVersion,
    String providerId,
    String providerVersion,
    List<Subject> subjects
) {
    public KycScreeningPolicy {
        subjects = subjects == null ? null : Collections.unmodifiableList(new ArrayList<>(subjects));
    }

    public static KycScreeningPolicy required(
        String policyVersion,
        String providerId,
        String providerVersion,
        List<Subject> subjects
    ) {
        return new KycScreeningPolicy(Requirement.REQUIRED, policyVersion, providerId, providerVersion, subjects);
    }

    public static KycScreeningPolicy notRequired(String policyVersion) {
        return new KycScreeningPolicy(Requirement.NOT_REQUIRED, policyVersion, null, null, List.of());
    }

    public enum Requirement {
        REQUIRED,
        NOT_REQUIRED
    }

    public record Subject(String subjectReference, Map<String, String> authorizedReferences) {
        public Subject {
            authorizedReferences = authorizedReferences == null
                ? null
                : Collections.unmodifiableMap(new HashMap<>(authorizedReferences));
        }
    }
}
