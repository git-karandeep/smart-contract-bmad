package ai.generalcontracts.startup.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = safeIdentifier(request.getHeader("X-Request-Id"));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        String correlationId = safeIdentifier(request.getHeader("X-Correlation-Id"));
        if (correlationId == null) {
            correlationId = requestId;
        }
        String transactionId = safeIdentifier(request.getHeader("X-Transaction-Id"));
        MDC.put("requestId", requestId);
        MDC.put("correlationId", correlationId);
        MDC.remove("transactionId");
        if (transactionId != null) {
            MDC.put("transactionId", transactionId);
        }
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("X-Correlation-Id", correlationId);
        if (transactionId != null) {
            response.setHeader("X-Transaction-Id", transactionId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            MDC.remove("correlationId");
            MDC.remove("transactionId");
        }
    }

    private String safeIdentifier(String value) {
        return value != null && SAFE_IDENTIFIER.matcher(value).matches() ? value : null;
    }
}
