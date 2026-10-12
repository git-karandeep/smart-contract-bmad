package ai.generalcontracts.startup.config.filters;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

public final class WorkSlateAthenticationFilter extends OncePerRequestFilter {
    private static final String CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";
    
    private final String trustStorePath;
    private final String trustStorePassword;
    private final String trustStoreType;

    public WorkSlateAthenticationFilter(
            @Value("${server.ssl.trust-store}") String trustStorePath,
            @Value("${server.ssl.trust-store-password}") String trustStorePassword,
            @Value("${server.ssl.trust-store-type:PKCS12}") String trustStoreType
    ) {
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
        this.trustStoreType = trustStoreType;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            X509Certificate[] certificates = (X509Certificate[]) request.getAttribute(CERTIFICATE_ATTRIBUTE);
            
            if (certificates == null || certificates.length == 0) {
                filterChain.doFilter(request, response);
                return;
            }

            X509Certificate clientCert = certificates[0];
            String clientCN = extractCommonName(clientCert);
            
            if (clientCN == null || clientCN.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\" forbidden\",\"reason\":\"invalid_certificate_common_name\"}");
                return;
            }

            if (!isTrustedCommonName(clientCN)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"forbidden\",\"reason\":\"untrusted_common_name\"}");
                return;
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"internal_server_error\",\"reason\":\"certificate_validation_failed\"}");
        }
    }

    private String extractCommonName(X509Certificate certificate) {
        String dn = certificate.getSubjectX500Principal().getName();
        for (String rdn : dn.split(",")) {
            rdn = rdn.trim();
            if (rdn.toUpperCase().startsWith("CN=")) {
                return rdn.substring(3);
            }
        }
        return null;
    }

    private boolean isTrustedCommonName(String commonName) {
        try (FileInputStream fis = new FileInputStream(trustStorePath.replace("file:", ""))) {
            KeyStore trustStore = KeyStore.getInstance(trustStoreType);
            trustStore.load(fis, trustStorePassword.toCharArray());
            
            Enumeration<String> aliases = trustStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                java.security.cert.Certificate cert = trustStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    X509Certificate trustedCert = (X509Certificate) cert;
                    String trustedCN = extractCommonName(trustedCert);
                    if (commonName.equals(trustedCN)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
