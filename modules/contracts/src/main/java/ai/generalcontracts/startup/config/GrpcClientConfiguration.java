package ai.generalcontracts.startup.config;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.URI;
import java.security.KeyStore;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Configuration
public class GrpcClientConfiguration {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(GrpcClientConfiguration.class);
    
    private final ConfigProperties configProperties;
    private final ResourceLoader resourceLoader;

    public GrpcClientConfiguration(ConfigProperties configProperties, ResourceLoader resourceLoader) {
        this.configProperties = configProperties;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public ManagedChannel grpcChannel(@Value("${camunda.client.grpc-address}") String grpcAddress) throws Exception {
        URI address = URI.create(grpcAddress.contains("://") ? grpcAddress : "http://" + grpcAddress);
        String host = address.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Camunda gRPC address must include a host");
        }
        int port = address.getPort() >= 0 ? address.getPort() : 26500;

        NettyChannelBuilder channelBuilder = NettyChannelBuilder.forAddress(host, port);

        if (configProperties.getSsl() != null && configProperties.getSsl().isEnabled()) {
            SslContext sslContext = createSslContext();
            channelBuilder = channelBuilder.sslContext(sslContext);
            log.info("gRPC client configured with SSL/TLS for {}:{}", host, port);
        } else {
            channelBuilder = channelBuilder.usePlaintext();
            log.info("gRPC client configured without SSL/TLS for {}:{}", host, port);
        }

        return channelBuilder.build();
    }

    private SslContext createSslContext() throws Exception {
        ConfigProperties.Ssl sslConfig = configProperties.getSsl();
        SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient();

        // Configure KeyManager for client certificate authentication (mTLS)
        if (sslConfig.getKeystore() != null && isNotBlank(sslConfig.getKeystore().getLocation())) {
            Resource keystoreResource = resourceLoader.getResource(sslConfig.getKeystore().getLocation());
            KeyStore keyStore = ClientConfiguration.getKeyStore(
                    keystoreResource,
                    sslConfig.getKeystore().getType(),
                    sslConfig.getKeystore().getProvider(),
                    sslConfig.getKeystore().getPassword()
            );

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(
                    keyStore,
                    Optional.ofNullable(defaultString(sslConfig.getKeyPassword(), sslConfig.getKeyStorePassword()))
                            .map(String::toCharArray)
                            .orElse(null)
            );
            sslContextBuilder.keyManager(keyManagerFactory);
            log.info("Configured client keystore: {}", sslConfig.getKeystore());
        }

        // Configure TrustManager for server certificate validation
        if (sslConfig.getTruststore() != null && isNotBlank(sslConfig.getTruststore().getLocation())) {
            Resource truststoreResource = resourceLoader.getResource(sslConfig.getTruststore().getLocation());
            KeyStore trustStore = ClientConfiguration.getKeyStore(
                    truststoreResource,
                    sslConfig.getTruststore().getType(),
                    sslConfig.getTruststore().getProvider(),
                    sslConfig.getTruststore().getPassword()
            );

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            sslContextBuilder.trustManager(trustManagerFactory);
            log.info("Configured client truststore: {}", sslConfig.getTruststore());
        } else if (!configProperties.getApi().isCheckServerCert()) {
            // Disable server certificate verification if configured
            sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            log.warn("Server certificate verification is DISABLED - INSECURE");
        }

        return sslContextBuilder.build();
    }
}
