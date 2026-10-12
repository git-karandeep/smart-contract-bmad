package ai.generalcontracts.startup.workflow;

public interface ProviderSecretResolver {
    byte[] resolveSecret(String providerId);
}
