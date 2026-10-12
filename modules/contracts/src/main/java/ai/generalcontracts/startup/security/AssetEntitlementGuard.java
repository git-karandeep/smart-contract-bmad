package ai.generalcontracts.startup.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Component;

@Component
public class AssetEntitlementGuard {
    @PreAuthorize("@assetEntitlementAuthorizer.hasPermission(authentication, #tenantId, #assetId, #permission)")
    public void requirePermission(
        @P("tenantId") String tenantId,
        @P("assetId") String assetId,
        @P("permission") AssetPermission permission
    ) {
    }
}
