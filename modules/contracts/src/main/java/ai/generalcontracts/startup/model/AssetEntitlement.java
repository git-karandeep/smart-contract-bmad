package ai.generalcontracts.startup.model;

import ai.generalcontracts.startup.security.AssetPermission;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "asset_entitlements")
@CompoundIndex(
    name = "principal_tenant_asset_permission_unique",
    def = "{'principalId': 1, 'tenantId': 1, 'assetId': 1, 'permission': 1}",
    unique = true
)
public class AssetEntitlement {
    @Id
    private String id;

    private String principalId;
    private String tenantId;
    private String assetId;
    private AssetPermission permission;

    public AssetEntitlement() {
    }

    public AssetEntitlement(
        String principalId,
        String tenantId,
        String assetId,
        AssetPermission permission
    ) {
        this.principalId = principalId;
        this.tenantId = tenantId;
        this.assetId = assetId;
        this.permission = permission;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public AssetPermission getPermission() {
        return permission;
    }

    public void setPermission(AssetPermission permission) {
        this.permission = permission;
    }
}
