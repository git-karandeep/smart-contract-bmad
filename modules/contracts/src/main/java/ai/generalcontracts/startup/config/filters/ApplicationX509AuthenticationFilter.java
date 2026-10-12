package ai.generalcontracts.startup.config.filters;

import ai.generalcontracts.startup.config.ClientConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter;

import java.io.IOException;


public class ApplicationX509AuthenticationFilter extends X509AuthenticationFilter {
    private static final Logger log = LoggerFactory.getLogger(ApplicationX509AuthenticationFilter.class);
    private final ClientConfiguration.WrapperTrustManager savingTrustManager;
    private final boolean sslBundleEnabled;

    public ApplicationX509AuthenticationFilter(ClientConfiguration.WrapperTrustManager savingTrustManager, boolean sslBundleEnabled) {
        this.savingTrustManager = savingTrustManager;
        this.sslBundleEnabled = sslBundleEnabled;
    }
    @SneakyThrows
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException, RuntimeException {
        log.info("Entering X509Auth filter");
        Object principal = getPreAuthenticatedPrincipal((HttpServletRequest) request);
        if (principal == null || !sslBundleEnabled) {
            chain.doFilter(request, response);
            return;
        }

        if (savingTrustManager == null
                || !savingTrustManager.checkServerTrusted(savingTrustManager.getAcceptedIssuers(), principal)) {
            log.info("Request UnAuthorized {} ", HttpServletResponse.SC_UNAUTHORIZED);
            ((HttpServletResponse)response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        chain.doFilter(request, response);
    }
}
