package ai.generalcontracts.startup.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.util.StringUtils;

@Configuration
@EnableMongoRepositories(basePackages = "ai.generalcontracts.startup.repository")
public class MongoClientConfig extends AbstractMongoClientConfiguration {
    private final MongoProperties mongoProperties;
    private final ResourceLoader resourceLoader;
    private final Environment environment;

    public MongoClientConfig(MongoProperties mongoProperties, ResourceLoader resourceLoader, Environment environment) {
        this.mongoProperties = mongoProperties;
        this.resourceLoader = resourceLoader;
        this.environment = environment;
    }

    @Override
    protected String getDatabaseName() {
        String database = resolveProperty("spring.data.mongodb.database", "spring.mongodb.database", mongoProperties.getDatabase());
        return StringUtils.hasText(database) ? database : "general-contracts";
    }

    @Override
    protected void configureClientSettings(com.mongodb.MongoClientSettings.Builder builder) {
        mongoDBDefaultSettings().customize(builder);
    }

    @Bean
    public MongoPropertiesClientSettingsBuilderCustomizer mongoDBDefaultSettings() {
        return new MongoPropertiesClientSettingsBuilderCustomizer(resourceLoader, mongoProperties, environment);
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), getDatabaseName());
    }

    private String resolveProperty(String modernKey, String legacyKey, String fallback) {
        String value = environment.getProperty(modernKey);
        if (!StringUtils.hasText(value)) {
            value = environment.getProperty(legacyKey);
        }
        if (!StringUtils.hasText(value)) {
            value = fallback;
        }
        return value;
    }
}
