package ai.generalcontracts.startup.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class ProductionProfileValidator {
    public ProductionProfileValidator(Environment environment) {
        validateRequired(environment, "spring.data.mongodb.uri", "MONGODB_URI");
        validateRequired(environment, "chain.rpc-url", "CHAIN_RPC_URL");
        validateRequired(environment, "chain.signer.key-ref", "SIGNER_KEY_REF");
        validateRequired(environment, "spring.security.user.name", "APP_SECURITY_USERNAME");
        validateRequired(environment, "spring.security.user.password", "APP_SECURITY_PASSWORD");
        if (!Boolean.parseBoolean(environment.getProperty("spring.data.mongodb.ssl.enabled", "true"))) {
            throw new IllegalStateException("Production MongoDB connections must enable TLS (MONGODB_SSL_ENABLED)");
        }
        if (Boolean.parseBoolean(environment.getProperty("spring.data.mongodb.ssl.invalid-hostname-allowed", "false"))) {
            throw new IllegalStateException("Production MongoDB TLS must validate the server hostname");
        }
    }

    private void validateRequired(Environment environment, String propertyName, String envVarName) {
        String value = environment.getProperty(propertyName);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                "Missing required production configuration property: " + propertyName + " (environment variable: " + envVarName + ")"
            );
        }
    }
}
