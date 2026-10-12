package ai.generalcontracts.startup.security;

import ai.generalcontracts.startup.observability.SecurityAuditLogger;
import ai.generalcontracts.startup.repository.AssetEntitlementRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component("assetEntitlementAuthorizer")
public class AssetEntitlementAuthorizer {
    private final AssetEntitlementRepository entitlements;
    private final SecurityAuditLogger auditLogger;

    public AssetEntitlementAuthorizer(
        AssetEntitlementRepository entitlements,
        SecurityAuditLogger auditLogger
    ) {
        this.entitlements = entitlements;
        this.auditLogger = auditLogger;
    }

    public boolean hasPermission(
        Authentication authentication,
        String requestedTenantId,
        String assetId,
        AssetPermission permission
    ) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
            || !jwtAuthentication.isAuthenticated()) {
            auditLogger.assetDecision(false, permission.name(), "unverified_principal");
            return false;
        }

        String principalId = jwtAuthentication.getToken().getSubject();
        String verifiedTenantId = jwtAuthentication.getToken().getClaimAsString("tenant_id");
        if (!StringUtils.hasText(principalId)
            || !StringUtils.hasText(verifiedTenantId)
            || !StringUtils.hasText(assetId)
            || !verifiedTenantId.equals(requestedTenantId)) {
            auditLogger.assetDecision(false, permission.name(), "tenant_or_identity_mismatch");
            return false;
        }

        boolean allowed = entitlements.existsByPrincipalIdAndTenantIdAndAssetIdAndPermission(
            principalId,
            verifiedTenantId,
            assetId,
            permission
        );
        auditLogger.assetDecision(allowed, permission.name(),
            allowed ? "entitlement_found" : "entitlement_missing");
        return allowed;
    }
}
