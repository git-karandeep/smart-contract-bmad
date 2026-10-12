package ai.generalcontracts.startup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "spring.security")
public class ConfigProperties {

    private Ssl ssl;
    private Api api;

    public Ssl getSsl() {
        return ssl;
    }

    public void setSsl(Ssl ssl) {
        this.ssl = ssl;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public static class Ssl {
        private boolean enabled = false;
        private boolean sslBundleEnabled = false;
        private Keystore keystore;
        private Truststore truststore;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSslBundleEnabled() {
            return sslBundleEnabled;
        }

        public void setSslBundleEnabled(boolean sslBundleEnabled) {
            this.sslBundleEnabled = sslBundleEnabled;
        }

        public Keystore getKeystore() {
            return keystore;
        }

        public void setKeystore(Keystore keystore) {
            this.keystore = keystore;
        }

        public Truststore getTruststore() {
            return truststore;
        }

        public void setTruststore(Truststore truststore) {
            this.truststore = truststore;
        }

        // Legacy methods for backward compatibility with ClientConfiguration
        public String getKeyStore() {
            return keystore != null ? keystore.getLocation() : null;
        }

        public String getKeyStoreType() {
            return keystore != null ? keystore.getType() : "PKCS12";
        }

        public String getKeyStoreProvider() {
            return keystore != null ? keystore.getProvider() : null;
        }

        public String getKeyStorePassword() {
            return keystore != null ? keystore.getPassword() : null;
        }

        public String getKeyPassword() {
            return keystore != null ? keystore.getKeyPassword() : null;
        }

        public String getKeyAlias() {
            return keystore != null ? keystore.getKeyAlias() : null;
        }

        public String getTrustStore() {
            return truststore != null ? truststore.getLocation() : null;
        }

        public String getTrustStoreType() {
            return truststore != null ? truststore.getType() : "PKCS12";
        }

        public String getTrustStoreProvider() {
            return truststore != null ? truststore.getProvider() : null;
        }

        public String getTrustStorePassword() {
            return truststore != null ? truststore.getPassword() : null;
        }
    }

    public static class Keystore {
        private String location;
        private String type = "PKCS12";
        private String provider;
        private String password;
        private String keyPassword;
        private String keyAlias;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        public void setKeyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
        }

        public String getKeyAlias() {
            return keyAlias;
        }

        public void setKeyAlias(String keyAlias) {
            this.keyAlias = keyAlias;
        }
    }

    public static class Truststore {
        private String location;
        private String type = "PKCS12";
        private String provider;
        private String password;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Api {
        private String baseUrl;
        private boolean checkServerCert = true;
        private HttpHeaders headers = new HttpHeaders();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean isCheckServerCert() {
            return checkServerCert;
        }

        public void setCheckServerCert(boolean checkServerCert) {
            this.checkServerCert = checkServerCert;
        }

        public HttpHeaders getHeaders() {
            return headers;
        }

        public void setHeaders(HttpHeaders headers) {
            this.headers = headers;
        }
    }
}
