package ai.generalcontracts.startup.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.generalcontracts.startup.observability.SecurityAuditLogger;
import ai.generalcontracts.startup.repository.AssetEntitlementRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AssetEntitlementAuthorizerTest {
    @Mock
    private AssetEntitlementRepository entitlements;

    @Mock
    private SecurityAuditLogger auditLogger;

    @InjectMocks
    private AssetEntitlementAuthorizer authorizer;

    @Test
    void authorizesOnlyTheVerifiedPrincipalTenantAssetAndPermissionTuple() {
        when(entitlements.existsByPrincipalIdAndTenantIdAndAssetIdAndPermission(
            "principal-a", "tenant-a", "asset-a", AssetPermission.READ
        )).thenReturn(true);

        assertThat(authorizer.hasPermission(
            authentication("principal-a", "tenant-a"),
            "tenant-a",
            "asset-a",
            AssetPermission.READ
        )).isTrue();

        verify(entitlements).existsByPrincipalIdAndTenantIdAndAssetIdAndPermission(
            "principal-a", "tenant-a", "asset-a", AssetPermission.READ
        );
    }

    @Test
    void deniesCrossTenantRequestsBeforeLookingUpTheOtherTenantAsset() {
        assertThat(authorizer.hasPermission(
            authentication("principal-a", "tenant-a"),
            "tenant-b",
            "asset-a",
            AssetPermission.READ
        )).isFalse();

        verify(entitlements, never()).existsByPrincipalIdAndTenantIdAndAssetIdAndPermission(
            "principal-a", "tenant-b", "asset-a", AssetPermission.READ
        );
    }

    @Test
    void deniesMissingEntitlementsAndUnauthenticatedPrincipals() {
        when(entitlements.existsByPrincipalIdAndTenantIdAndAssetIdAndPermission(
            "principal-a", "tenant-a", "asset-missing", AssetPermission.WRITE
        )).thenReturn(false);

        assertThat(authorizer.hasPermission(
            authentication("principal-a", "tenant-a"),
            "tenant-a",
            "asset-missing",
            AssetPermission.WRITE
        )).isFalse();
        assertThat(authorizer.hasPermission(
            new AnonymousAuthenticationToken(
                "test-key",
                "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
            ),
            "tenant-a",
            "asset-a",
            AssetPermission.READ
        )).isFalse();
    }

    private JwtAuthenticationToken authentication(String principal, String tenant) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("synthetic-test-token")
            .header("alg", "RS256")
            .subject(principal)
            .claim("tenant_id", tenant)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .build();
        return new JwtAuthenticationToken(jwt, java.util.List.of());
    }
}
