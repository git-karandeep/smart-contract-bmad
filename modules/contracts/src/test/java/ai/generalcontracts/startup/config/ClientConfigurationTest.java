package ai.generalcontracts.startup.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;

class ClientConfigurationTest {
    @Test
    void buildsWebClientWhenOptionalKeyStoresAreUnconfigured() throws Exception {
        ConfigProperties properties = new ConfigProperties();
        ConfigProperties.Ssl ssl = new ConfigProperties.Ssl();
        ssl.setKeystore(new ConfigProperties.Keystore());
        ssl.setTruststore(new ConfigProperties.Truststore());
        properties.setSsl(ssl);
        ConfigProperties.Api api = new ConfigProperties.Api();
        api.setBaseUrl("https://example.test");
        properties.setApi(api);

        ClientConfiguration configuration = new ClientConfiguration(
            properties,
            new DefaultResourceLoader(),
            null
        );

        assertThat(configuration.webClient()).isNotNull();
    }

    @Test
    void usesDefaultProviderWhenConfiguredProviderIsBlank() throws Exception {
        char[] password = "test-password".toCharArray();
        KeyStore source = KeyStore.getInstance("PKCS12");
        source.load(null, password);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        source.store(encoded, password);

        KeyStore loaded = ClientConfiguration.getKeyStore(
            new ByteArrayResource(encoded.toByteArray()),
            "PKCS12",
            "",
            "test-password"
        );

        assertThat(loaded.getType()).isEqualTo("PKCS12");
    }
}
