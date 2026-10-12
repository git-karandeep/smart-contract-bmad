package ai.generalcontracts.startup.security;

import ai.generalcontracts.startup.RequestPathResolver;
import ai.generalcontracts.startup.observability.SecurityAuditLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class ClientCertificateFilter extends OncePerRequestFilter {
    private static final String CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";
    private final SecurityAuditLogger auditLogger;

    public ClientCertificateFilter(SecurityAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = RequestPathResolver.resolve(request);
        if (request.getMethod().equals("GET")
            && (path.equals("/actuator/health") || path.startsWith("/actuator/health/"))) {
            return true;
        }
        return !(path.equals("/orders")
            || path.equals("/api/chain/status")
            || path.equals("/actuator")
            || path.startsWith("/actuator/"));
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken
            || hasTrustedCertificate(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        auditLogger.clientCertificateDenied(request);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"forbidden\"}");
    }

    private boolean hasTrustedCertificate(HttpServletRequest request) {
        Object attribute = request.getAttribute(CERTIFICATE_ATTRIBUTE);
        if (!(attribute instanceof X509Certificate[] certificates)) {
            return false;
        }
        return Arrays.stream(certificates).anyMatch(certificate -> certificate != null);
    }

}
