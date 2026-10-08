package ai.generalcontracts.startup.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.core.io.DefaultResourceLoader;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class MongoPropertiesClientSettingsBuilderCustomizerTest {
    @Test
    void rejectsIncompleteMongoCredentials() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.data.mongodb.username", "reader");
        MongoProperties properties = new MongoProperties();
        MongoPropertiesClientSettingsBuilderCustomizer customizer =
            new MongoPropertiesClientSettingsBuilderCustomizer(
                new DefaultResourceLoader(),
                properties,
                environment
            );

        assertThatThrownBy(() -> customizer.customize(com.mongodb.MongoClientSettings.builder()))
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("MongoDB username and password must be provided together");
    }
}
