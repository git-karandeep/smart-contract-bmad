package ai.generalcontracts.startup.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

class ProductionProfileValidatorTest {
    @ParameterizedTest
    @MethodSource("requiredProperties")
    void rejectsEachMissingProductionSetting(String property, String environmentVariable) {
        MockEnvironment environment = validEnvironment().withProperty(property, "");

        assertThatThrownBy(() -> new ProductionProfileValidator(environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(property)
            .hasMessageContaining(environmentVariable);
    }

    @Test
    void acceptsCompleteProductionSettingsWithVerifiedTls() {
        assertThatCode(() -> new ProductionProfileValidator(validEnvironment())).doesNotThrowAnyException();
    }

    @Test
    void rejectsMongoTlsWhenDisabledOrHostnameVerificationIsBypassed() {
        assertThatThrownBy(() -> new ProductionProfileValidator(
            validEnvironment().withProperty("spring.data.mongodb.ssl.enabled", "false")
        )).hasMessageContaining("must enable TLS");

        assertThatThrownBy(() -> new ProductionProfileValidator(
            validEnvironment().withProperty("spring.data.mongodb.ssl.invalid-hostname-allowed", "true")
        )).hasMessageContaining("must validate the server hostname");

        assertThatThrownBy(() -> new ProductionProfileValidator(
            validEnvironment().withProperty("server.ssl.client-auth", "none")
        )).hasMessageContaining("must request client certificates");
    }

    private static Stream<Arguments> requiredProperties() {
        return Stream.of(
            Arguments.of("spring.data.mongodb.uri", "MONGODB_URI"),
            Arguments.of("chain.rpc-url", "CHAIN_RPC_URL"),
            Arguments.of("chain.signer.key-ref", "SIGNER_KEY_REF"),
            Arguments.of("app.security.jwt.issuer", "APP_SECURITY_JWT_ISSUER"),
            Arguments.of("app.security.jwt.audience", "APP_SECURITY_JWT_AUDIENCE"),
            Arguments.of("server.ssl.key-store", "SERVER_SSL_KEY_STORE_PATH"),
            Arguments.of("server.ssl.key-store-password", "SERVER_SSL_KEY_STORE_PASSWORD"),
            Arguments.of("server.ssl.trust-store", "SERVER_SSL_TRUST_STORE_PATH"),
            Arguments.of("server.ssl.trust-store-password", "SERVER_SSL_TRUST_STORE_PASSWORD")
        );
    }

    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
            .withProperty("spring.data.mongodb.uri", "mongodb://example.invalid/db")
            .withProperty("chain.rpc-url", "http://127.0.0.1:8545")
            .withProperty("chain.signer.key-ref", "test-key-reference")
            .withProperty("app.security.jwt.issuer", "https://issuer.example.invalid/")
            .withProperty("app.security.jwt.audience", "contracts-api")
            .withProperty("server.ssl.key-store", "file:/run/secrets/server-keystore.p12")
            .withProperty("server.ssl.key-store-password", "test-key-store-password")
            .withProperty("server.ssl.trust-store", "file:/run/secrets/client-truststore.p12")
            .withProperty("server.ssl.trust-store-password", "test-trust-store-password")
            .withProperty("server.ssl.enabled", "true")
            .withProperty("server.ssl.client-auth", "want")
            .withProperty("spring.data.mongodb.ssl.enabled", "true")
            .withProperty("spring.data.mongodb.ssl.invalid-hostname-allowed", "false");
    }
}
