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
        validateRequired(environment, "app.security.jwt.issuer", "APP_SECURITY_JWT_ISSUER");
        validateRequired(environment, "app.security.jwt.audience", "APP_SECURITY_JWT_AUDIENCE");
        validateRequired(environment, "server.ssl.key-store", "SERVER_SSL_KEY_STORE_PATH");
        validateRequired(environment, "server.ssl.key-store-password", "SERVER_SSL_KEY_STORE_PASSWORD");
        validateRequired(environment, "server.ssl.trust-store", "SERVER_SSL_TRUST_STORE_PATH");
        validateRequired(environment, "server.ssl.trust-store-password", "SERVER_SSL_TRUST_STORE_PASSWORD");
        if (!Boolean.parseBoolean(environment.getProperty("server.ssl.enabled", "false"))) {
            throw new IllegalStateException("Production HTTPS must be enabled (SERVER_SSL_ENABLED)");
        }
        if (!"want".equalsIgnoreCase(environment.getProperty("server.ssl.client-auth", ""))) {
            throw new IllegalStateException(
                "Production TLS must request client certificates for route-level mTLS (SERVER_SSL_CLIENT_AUTH=want)"
            );
        }
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
