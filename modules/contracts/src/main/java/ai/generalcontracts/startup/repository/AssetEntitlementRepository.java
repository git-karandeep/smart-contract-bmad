package ai.generalcontracts.startup.repository;

import ai.generalcontracts.startup.model.AssetEntitlement;
import ai.generalcontracts.startup.security.AssetPermission;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AssetEntitlementRepository extends MongoRepository<AssetEntitlement, String> {
    boolean existsByPrincipalIdAndTenantIdAndAssetIdAndPermission(
        String principalId,
        String tenantId,
        String assetId,
        AssetPermission permission
    );
}
