package ai.generalcontracts.startup.observability;

import ai.generalcontracts.startup.RequestPathResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAuditLogger.class);

    public void authorizationDecision(HttpServletRequest request, boolean allowed, String permission) {
        String decision = allowed ? "allow" : "deny";
        String correlationId = correlationId();
        if (allowed) {
            LOGGER.info(
                "security.authorization decision={} permission={} route={} correlationId={}",
                decision,
                permission,
                route(request),
                correlationId
            );
        } else {
            LOGGER.warn(
                "security.authorization decision={} permission={} route={} correlationId={}",
                decision,
                permission,
                route(request),
                correlationId
            );
        }
    }

    public void authenticationDenied(HttpServletRequest request) {
        LOGGER.warn("security.authentication decision=deny route={} correlationId={}",
            route(request), correlationId());
    }

    public void authorizationDenied(HttpServletRequest request) {
        LOGGER.warn("security.authorization decision=deny route={} correlationId={}",
            route(request), correlationId());
    }

    public void clientCertificateDenied(HttpServletRequest request) {
        LOGGER.warn("security.client_certificate decision=deny route={} correlationId={}",
            route(request), correlationId());
    }

    public void assetDecision(boolean allowed, String permission, String reason) {
        String decision = allowed ? "allow" : "deny";
        if (allowed) {
            LOGGER.info("security.asset decision={} permission={} reason={} correlationId={}",
                decision, permission, reason, correlationId());
        } else {
            LOGGER.warn("security.asset decision={} permission={} reason={} correlationId={}",
                decision, permission, reason, correlationId());
        }
    }

    private String route(HttpServletRequest request) {
        String path = RequestPathResolver.resolve(request);
        if (path.equals("/orders")) {
            return request.getMethod().equals("GET") ? "orders.read" : "orders.write";
        }
        if (path.equals("/api/chain/status")) {
            return "chain.status";
        }
        if (path.equals("/actuator") || path.startsWith("/actuator/")) {
            return "actuator";
        }
        return "unmatched";
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id == null ? "unavailable" : id;
    }
}
