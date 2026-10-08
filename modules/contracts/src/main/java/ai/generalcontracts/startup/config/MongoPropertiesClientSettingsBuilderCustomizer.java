package ai.generalcontracts.startup.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

public class MongoPropertiesClientSettingsBuilderCustomizer implements MongoClientSettingsBuilderCustomizer, Ordered {
    private final ResourceLoader resourceLoader;
    private final MongoProperties properties;
    private final Environment environment;
    private final boolean sslEnabled;
    private final boolean invalidHostnameAllowed;
    private final String mongoCertFilePath;
    private int order = 0;

    public MongoPropertiesClientSettingsBuilderCustomizer(ResourceLoader resourceLoader, MongoProperties properties, Environment environment) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
        this.environment = environment;
        this.sslEnabled = Boolean.parseBoolean(resolveProperty("spring.data.mongodb.ssl.enabled", "spring.mongodb.ssl.enabled", "false"));
        this.invalidHostnameAllowed = Boolean.parseBoolean(resolveProperty("spring.data.mongodb.ssl.invalid-hostname-allowed", "spring.mongodb.ssl.invalid-hostname-allowed", "false"));
        this.mongoCertFilePath = resolveProperty("spring.data.mongodb.ssl.certificate", "spring.mongodb.ssl.certificate", "");
    }

    @Override
    public void customize(MongoClientSettings.Builder settingsBuilder) {
        applyUuidRepresentation(settingsBuilder);
        applyHostAndPort(settingsBuilder);
        applyReplicaSet(settingsBuilder);
        settingsBuilder.applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(5, TimeUnit.SECONDS));
        settingsBuilder.applyToSocketSettings(socket -> socket
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS));
        try {
            if (sslEnabled) {
                applySslSettings(settingsBuilder);
            }
            applyCredentials(settingsBuilder);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure MongoDB client settings", e);
        }
    }

    private void applySslSettings(MongoClientSettings.Builder settingsBuilder) {
        SSLContext sslContext = createCustomSslContext();
        settingsBuilder
            .applyToSslSettings(builder -> builder
                .enabled(true)
                .invalidHostNameAllowed(invalidHostnameAllowed)
                .context(sslContext));
    }

    private SSLContext createCustomSslContext() {
        try {
            if (!StringUtils.hasText(mongoCertFilePath)) {
                return SSLContext.getDefault();
            }

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Resource resource = resourceLoader.getResource(mongoCertFilePath);

            try (InputStream is = resource.getInputStream()) {
                X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(is);
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null);
                keyStore.setCertificateEntry("caCert", cert);
                trustManagerFactory.init(keyStore);
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
                return sslContext;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load MongoDB TLS certificate from " + mongoCertFilePath, e);
        }
    }

    private void applyUuidRepresentation(MongoClientSettings.Builder settingsBuilder) {
        if (properties.getRepresentation() != null && properties.getRepresentation().getUuid() != null) {
            settingsBuilder.uuidRepresentation(properties.getRepresentation().getUuid());
        }
    }

    private void applyHostAndPort(MongoClientSettings.Builder settings) {
        String connectionUri = resolveConnectionUri();
        if (StringUtils.hasText(connectionUri)) {
            settings.applyConnectionString(new ConnectionString(connectionUri));
            return;
        }

        String host = getPropertyValue("spring.data.mongodb.host", "spring.mongodb.host", properties.getHost(), "localhost");
        Integer port = readPort();
        ServerAddress serverAddress = new ServerAddress(host, port);
        List<ServerAddress> serverAddressList = new ArrayList<>(List.of(serverAddress));
        applyAdditionalHosts(serverAddressList);
        settings.applyToClusterSettings(cluster -> cluster.hosts(serverAddressList));
    }

    private Integer readPort() {
        Integer configuredPort = properties.getPort();
        String portFallback = configuredPort == null ? null : configuredPort.toString();
        String portProperty = getPropertyValue("spring.data.mongodb.port", "spring.mongodb.port", portFallback, "27017");
        return Integer.parseInt(portProperty);
    }

    private void applyAdditionalHosts(List<ServerAddress> serverAddressList) {
        List<String> additionalHosts = properties.getAdditionalHosts();
        String legacyHosts = environment.getProperty("spring.mongodb.additional-hosts");
        if (additionalHosts == null || additionalHosts.isEmpty()) {
            additionalHosts = new ArrayList<>();
            if (StringUtils.hasText(legacyHosts)) {
                for (String host : legacyHosts.split(",")) {
                    if (StringUtils.hasText(host)) {
                        additionalHosts.add(host.trim());
                    }
                }
            }
        }
        for (String host : additionalHosts) {
            if (StringUtils.hasText(host)) {
                serverAddressList.add(new ServerAddress(host));
            }
        }
    }

    private void applyCredentials(MongoClientSettings.Builder builder) {
        String username = getPropertyValue("spring.data.mongodb.username", "spring.mongodb.username", properties.getUsername(), null);
        String password = getPropertyValue("spring.data.mongodb.password", "spring.mongodb.password", properties.getPassword() == null ? null : new String(properties.getPassword()), null);
        boolean hasUsername = StringUtils.hasText(username);
        boolean hasPassword = StringUtils.hasText(password);
        if (hasUsername != hasPassword) {
            throw new IllegalStateException("MongoDB username and password must be provided together");
        }
        if (!hasUsername) {
            return;
        }

        String database = getPropertyValue("spring.data.mongodb.authentication-database", "spring.mongodb.authentication-database",
            properties.getAuthenticationDatabase(), properties.getDatabase());
        builder.credential(MongoCredential.createCredential(username, database, password.toCharArray()));
    }

    private void applyReplicaSet(MongoClientSettings.Builder builder) {
        String replicaSetName = getPropertyValue("spring.data.mongodb.replica-set-name", "spring.mongodb.replica-set-name", properties.getReplicaSetName(), null);
        if (StringUtils.hasText(replicaSetName)) {
            builder.applyToClusterSettings(cluster -> cluster.requiredReplicaSetName(replicaSetName));
        }
    }

    private String resolveConnectionUri() {
        String uri = getPropertyValue("spring.data.mongodb.uri", "spring.mongodb.uri", properties.getUri(), null);
        if (StringUtils.hasText(uri)) {
            return uri;
        }
        return null;
    }

    private String getPropertyValue(String modernKey, String legacyKey, String fallback, String defaultValue) {
        String value = environment.getProperty(modernKey);
        if (!StringUtils.hasText(value)) {
            value = environment.getProperty(legacyKey);
        }
        if (!StringUtils.hasText(value)) {
            value = fallback;
        }
        if (!StringUtils.hasText(value)) {
            value = defaultValue;
        }
        return value;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    private String resolveProperty(String modernKey, String legacyKey, String fallback) {
        String value = environment.getProperty(modernKey);
        if (!StringUtils.hasText(value)) {
            value = environment.getProperty(legacyKey);
        }
        return StringUtils.hasText(value) ? value : fallback;
    }
}
