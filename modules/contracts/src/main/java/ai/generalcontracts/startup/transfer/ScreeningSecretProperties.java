package ai.generalcontracts.startup.transfer;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "screening.provider-secrets")
public class ScreeningSecretProperties {
    private Map<String, String> keys = new HashMap<>();

    public Map<String, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys == null ? new HashMap<>() : new HashMap<>(keys);
    }
}
