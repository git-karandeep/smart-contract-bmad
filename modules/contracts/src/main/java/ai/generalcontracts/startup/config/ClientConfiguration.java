package ai.generalcontracts.startup.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.defaultString;

@Configuration
public class ClientConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ClientConfiguration.class);
    private static final int MAXBUFSIZE = 2 * 1024 * 1024;
    private final ResourceLoader resourceLoader;
    private final SslBundles sslBundles;
    private final ConfigProperties configProperties;
    public ClientConfiguration(ConfigProperties configProperties, ResourceLoader resourceLoader, @Nullable SslBundles sslBundles) {
        this.configProperties = configProperties;
        this.resourceLoader = resourceLoader;
        this.sslBundles = sslBundles;
    }
    @Bean
    public WrapperTrustManager wrapperTrustManager() throws Exception {
        if (sslBundles != null && configProperties.getSsl().isSslBundleEnabled()) {
            TrustManagerFactory sslBundledTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            X509TrustManager defaultX509CertificateTrustManager = getDefaultX509CertificateTrustManager();
            SslBundle sslBundle = sslBundles.getBundle("secure-service");
            sslBundledTrustManagerFactory = sslBundle.getManagers().getTrustManagerFactory();
            log.info("Loaded SSLBundle {}", sslBundle.getProtocol());
            if (sslBundledTrustManagerFactory != null) {
                log.info("Loaded SSLBundle into TrustManager");
                //handshake certificate chain for X509AuthFilter
                X509TrustManager[] sslBundledTrustManager =
                        Arrays.stream(sslBundledTrustManagerFactory.getTrustManagers())
                                .filter(X509TrustManager.class::isInstance)
                                .map(X509TrustManager.class::cast)
                                .toArray(X509TrustManager[]::new);
                return new WrapperTrustManager(sslBundledTrustManager, defaultX509CertificateTrustManager);
            }
        }
        return null;
    }

    @Bean
    public WebClient webClient() throws Exception {
        TrustManagerFactory sslBundledTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        WrapperTrustManager saveTrustManager = wrapperTrustManager();
        X509TrustManager defaultX509CertificateTrustManager = getDefaultX509CertificateTrustManager();
        if (sslBundles != null && configProperties.getSsl().isSslBundleEnabled()) {
            SslBundle sslBundle = sslBundles.getBundle("secure-service");
            sslBundledTrustManagerFactory = sslBundle.getManagers().getTrustManagerFactory();
            log.info("Loaded SSLBundle {}", sslBundle.getProtocol());
        }

        ConfigProperties.Ssl sslConfig = configProperties.getSsl();
        KeyManagerFactory keyManagerFactory = null;

        // Configure KeyManager for client certificate authentication (mTLS)
        if (sslConfig != null && sslConfig.getKeystore() != null
                && StringUtils.isNotBlank(sslConfig.getKeystore().getLocation())) {
            KeyStore keyStore = getKeyStore(
                    resourceLoader.getResource(sslConfig.getKeystore().getLocation()),
                    sslConfig.getKeyStoreType(),
                    sslConfig.getKeyStoreProvider(),
                    sslConfig.getKeyStorePassword()
            );
            keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(
                    keyStore,
                    Optional.ofNullable(defaultString(sslConfig.getKeyPassword(), sslConfig.getKeyStorePassword()))
                            .map(String::toCharArray)
                            .orElse(null)
            );
            if (StringUtils.isNotBlank(sslConfig.getKeyAlias())) {
                KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
                if (keyManagers != null) {
                    for (int idx = 0; idx < keyManagers.length; idx++) {
                        if (keyManagers[idx] instanceof X509ExtendedKeyManager) {
                            keyManagers[idx] = new AliasedX509ExtendedKeyManagerWrapper(sslConfig.getKeyAlias(), (X509ExtendedKeyManager) keyManagers[idx]);
                        }
                    }
                }
            }
            log.info("Configured WebClient with client keystore: {}", sslConfig.getKeystore());
        }

        return getWebClientBuilder(keyManagerFactory, saveTrustManager);
    }
    /*
    Usage: Return WebClient from FactoryBuilder
     */
    private WebClient getWebClientBuilder(
            KeyManagerFactory keyManagerFactory,
            WrapperTrustManager saveTrustManager )
            throws Exception {
        SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();

        // Configure KeyManager if available (for mTLS)
        if (keyManagerFactory != null) {
            sslContextBuilder.keyManager(keyManagerFactory);
        }

        // Configure TrustManager for server certificate validation
        ConfigProperties.Ssl sslConfig = configProperties.getSsl();
        if (sslConfig != null && sslConfig.getTruststore() != null
                && StringUtils.isNotBlank(sslConfig.getTruststore().getLocation())) {
            KeyStore trustStore = getKeyStore(
                    resourceLoader.getResource(sslConfig.getTruststore().getLocation()),
                    sslConfig.getTruststore().getType(),
                    sslConfig.getTruststore().getProvider(),
                    sslConfig.getTruststore().getPassword()
            );
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            sslContextBuilder.trustManager(trustManagerFactory);
            log.info("Configured WebClient with client truststore: {}", sslConfig.getTruststore());
        } else if (!configProperties.getApi().isCheckServerCert()) {
            // Disable server certificate verification if configured
            sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            log.warn("Server certificate verification is DISABLED for WebClient - INSECURE");
        } else if (configProperties.getApi().isCheckServerCert() && configProperties.getSsl().isSslBundleEnabled() && saveTrustManager != null) {
            // Use SSL bundle trust manager if configured
            sslContextBuilder.trustManager(saveTrustManager);
        }

        SslContext context = sslContextBuilder.build();
        HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(context));
        final ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAXBUFSIZE))
                .build();
        return WebClient
                .builder()
                .exchangeStrategies(strategies)
                .baseUrl(configProperties.getApi().getBaseUrl())
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.addAll(configProperties.getApi().getHeaders());
                })
                .clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }
    private X509TrustManager getDefaultX509CertificateTrustManager() throws NoSuchAlgorithmException, KeyStoreException {
        X509TrustManager defaultX509CertificateTrustManager = null;
        TrustManagerFactory defaultTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaultTrustManagerFactory.init((KeyStore) null);
        for (TrustManager trustManager : defaultTrustManagerFactory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                defaultX509CertificateTrustManager = x509TrustManager;
                break;
            }
        }
        return defaultX509CertificateTrustManager;
    }
    public static KeyStore getKeyStore(Resource store, String storeType, String storeProvider, String storePassword) throws Exception {
        KeyStore keystore = null;
        if (store != null) {
            Objects.requireNonNull(storeType, "SSL keyStoreType cannot be null");
            if (StringUtils.isNotBlank(storeProvider)) {
                keystore = KeyStore.getInstance(storeType, storeProvider);
            } else {
                keystore = KeyStore.getInstance(storeType);
            }
            if (!store.exists()) {
                throw new IllegalStateException(store.getFilename() + " is not a valid keystore");
            }
            try (InputStream inStream = store.getInputStream()) {
                keystore.load(inStream, storePassword == null ? null : storePassword.toCharArray());
            }
        }
        return keystore;
    }
    public static class WrapperTrustManager implements X509TrustManager {
        private final X509TrustManager[] finalTrustManager;
        private final X509TrustManager defaultTrustManager;
        WrapperTrustManager(X509TrustManager[] finalTrustManager, X509TrustManager defaultTrustManager) {
            this.finalTrustManager = finalTrustManager;
            this.defaultTrustManager = defaultTrustManager;
        }
        private X509Certificate[] mergeCertificates() {
            ArrayList<X509Certificate> resultingCerts = new ArrayList<>();
            X509Certificate[] certificates = Arrays.stream(finalTrustManager)
                    .map(tm -> tm.getAcceptedIssuers())
                    .flatMap(obj -> Stream.of(obj))
                    .distinct()
                    .toArray(X509Certificate[]::new);
            resultingCerts.addAll(Arrays.asList(certificates));
            resultingCerts.addAll(Arrays.asList(defaultTrustManager.getAcceptedIssuers()));
            return resultingCerts.toArray(new X509Certificate[resultingCerts.size()]);
        }
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return mergeCertificates();
        }
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            defaultTrustManager.checkClientTrusted(mergeCertificates(), authType);
        }
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            Arrays.stream(finalTrustManager).forEach(tm -> {
                try {
                    tm.checkServerTrusted(chain, String.valueOf(authType));
                } catch (CertificateException e) {
                    try {
                        defaultTrustManager.checkServerTrusted(chain, authType);
                    } catch (CertificateException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });
        }
        public boolean checkServerTrusted(X509Certificate[] x509Certificates, Object authType) throws CertificateException {
            checkServerTrusted(x509Certificates, String.valueOf(authType));
            return Arrays.stream(x509Certificates).anyMatch(cert ->
                    cert.getSubjectX500Principal().getName().contains(String.valueOf(authType))
            );
        }
    }
    public static class AliasedX509ExtendedKeyManagerWrapper extends X509ExtendedKeyManager {
        private final String alias;
        private final X509ExtendedKeyManager delegate;
        public AliasedX509ExtendedKeyManagerWrapper(String alias, X509ExtendedKeyManager keyManager) {
            this.alias = alias;
            this.delegate = keyManager;
        }
        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return delegate.getServerAliases(keyType, issuers);
        }
        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return delegate.chooseServerAlias(keyType, issuers, socket);
        }
        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return delegate.getClientAliases(keyType, issuers);
        }
        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return alias;
        }
        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return delegate.getCertificateChain(alias);
        }
        @Override
        public PrivateKey getPrivateKey(String alias) {
            return delegate.getPrivateKey(alias);
        }
        @Override
        public String chooseEngineClientAlias(
                String[] keyType,
                Principal[] issuers, SSLEngine engine
        ) {
            return delegate.chooseEngineClientAlias(keyType, issuers, engine);
        }
        @Override
        public String chooseEngineServerAlias(
                String keyType,
                Principal[] issuers, SSLEngine engine
        ) {
            return delegate.chooseEngineServerAlias(keyType, issuers, engine);
        }
    }
}