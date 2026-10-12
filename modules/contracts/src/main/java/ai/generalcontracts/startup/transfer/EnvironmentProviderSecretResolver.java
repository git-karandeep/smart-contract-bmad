package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.startup.workflow.ProviderSecretResolver;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentProviderSecretResolver implements ProviderSecretResolver {
    private final ScreeningSecretProperties properties;

    public EnvironmentProviderSecretResolver(ScreeningSecretProperties properties) {
        this.properties = properties;
    }

    @Override
    public byte[] resolveSecret(String providerId) {
        String value = properties.getKeys().get(providerId);
        return value == null || value.isBlank() ? null : value.getBytes(StandardCharsets.UTF_8);
    }
}
