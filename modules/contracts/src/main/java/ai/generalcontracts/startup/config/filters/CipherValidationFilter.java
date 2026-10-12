package ai.generalcontracts.startup.config.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

public final class CipherValidationFilter extends OncePerRequestFilter {
    private static final String CIPHER_SUITE_ATTRIBUTE = "jakarta.servlet.request.cipher_suite";
    private static final Set<String> disallowedCiphers = Set.of();

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String cipherSuite = (String) request.getAttribute(CIPHER_SUITE_ATTRIBUTE);
        
        if (cipherSuite != null && disallowedCiphers.contains(cipherSuite)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"forbidden\",\"reason\":\"disallowed_cipher_suite\"}");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
