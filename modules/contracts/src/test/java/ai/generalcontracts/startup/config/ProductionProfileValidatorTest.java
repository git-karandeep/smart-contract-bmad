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
    }

    private static Stream<Arguments> requiredProperties() {
        return Stream.of(
            Arguments.of("spring.data.mongodb.uri", "MONGODB_URI"),
            Arguments.of("chain.rpc-url", "CHAIN_RPC_URL"),
            Arguments.of("chain.signer.key-ref", "SIGNER_KEY_REF"),
            Arguments.of("spring.security.user.name", "APP_SECURITY_USERNAME"),
            Arguments.of("spring.security.user.password", "APP_SECURITY_PASSWORD")
        );
    }

    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
            .withProperty("spring.data.mongodb.uri", "mongodb://example.invalid/db")
            .withProperty("chain.rpc-url", "http://127.0.0.1:8545")
            .withProperty("chain.signer.key-ref", "test-key-reference")
            .withProperty("spring.security.user.name", "test-user")
            .withProperty("spring.security.user.password", "test-password")
            .withProperty("spring.data.mongodb.ssl.enabled", "true")
            .withProperty("spring.data.mongodb.ssl.invalid-hostname-allowed", "false");
    }
}
