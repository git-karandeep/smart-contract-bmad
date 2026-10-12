package ai.generalcontracts.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.generalcontracts.startup.observability.RequestCorrelationFilter;
import ai.generalcontracts.startup.config.filters.CipherValidationFilter;
import ai.generalcontracts.startup.repository.AssetEntitlementRepository;
import ai.generalcontracts.startup.security.ClientCertificateFilter;
import ai.generalcontracts.startup.security.AssetEntitlementGuard;
import ai.generalcontracts.startup.security.AssetPermission;
import ai.generalcontracts.startup.service.OrderService;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(
    properties = {
        "management.health.mongo.enabled=false",
        "spring.mongodb.uri=mongodb://127.0.0.1:1/health-test?directConnection=true",
        "spring.mongodb.ssl.enabled=false"
    }
)
@ExtendWith(OutputCaptureExtension.class)
@Import(SecurityConfigurationTest.SyntheticAssetConfiguration.class)
@ActiveProfiles("test")
class SecurityConfigurationTest {
    private MockMvc mockMvc;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private AssetEntitlementRepository entitlementRepository;

    @Autowired
    private SyntheticAssetOperation syntheticAssetOperation;

    @BeforeEach
    void setUp() {
        when(orderService.getAll()).thenReturn(List.of());
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilters(applicationContext.getBean(RequestCorrelationFilter.class))
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    @Test
    void keepsOnlyHealthEndpointsPublicAndDeniesUnknownRoutes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Security-Policy",
                org.hamcrest.Matchers.containsString(
                    "default-src 'none'; base-uri 'none'; frame-ancestors 'none'"
                )));

        mockMvc.perform(get("/unlisted"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().json("{\"error\":\"unauthorized\"}"))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("SecurityFilterChain"))));

        mockMvc.perform(get("/unlisted")
                .with(jwt().jwt(token -> token.subject("user-a").claim("scope", "ORDER_READ")))
                .with(trustedCertificate()))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingMalformedAndInsufficientCredentialsWithoutDetails() throws Exception {
        mockMvc.perform(get("/orders"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().json("{\"error\":\"unauthorized\"}"));

        mockMvc.perform(get("/orders").header("Authorization", "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("not-a-jwt"))));

        mockMvc.perform(get("/orders")
                .with(jwt().jwt(token -> token.subject("user-a").claim("scope", "CHAIN_STATUS_READ")))
                .with(trustedCertificate()))
            .andExpect(status().isForbidden())
            .andExpect(content().json("{\"error\":\"forbidden\"}"));
    }

    @Test
    void requiresAClientCertificateAfterJwtAuthenticationAndAllowsApprovedScope() throws Exception {
        mockMvc.perform(get("/orders")
                .with(jwt().jwt(token -> token.subject("user-a").claim("scope", "ORDER_READ"))))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/orders")
                .with(jwt().jwt(token -> token.subject("user-a").claim("scope", "ORDER_READ")))
                .with(trustedCertificate()))
            .andExpect(status().isOk());
    }

    @Test
    void doesNotAcceptForwardedCertificateHeadersAsTransportIdentity() throws Exception {
        mockMvc.perform(get("/orders")
                .with(jwt().jwt(token -> token.subject("user-a").claim("scope", "ORDER_READ")))
                .header("X-Forwarded-Client-Cert", "forged-certificate"))
            .andExpect(status().isForbidden());
    }

    @Test
    void disablesCsrfForStatelessBearerRequestsAndEnforcesWriteScope() throws Exception {
        String invalidOrder = "{\"customer\":\"\",\"amount\":0,\"status\":\"\"}";

        mockMvc.perform(post("/orders")
                .with(jwt().jwt(token -> token.subject("user-a").claim("scope", "ORDER_READ")))
                .with(trustedCertificate())
                .contentType("application/json")
                .content(invalidOrder))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/orders")
                .with(jwt().jwt(token -> token.subject("user-a").claim("scope", "ORDER_WRITE")))
                .with(trustedCertificate())
                .contentType("application/json")
                .content(invalidOrder))
            .andExpect(status().isBadRequest());
    }

    @Test
    void protectsNonHealthActuatorRoutesWithOpsScopeAndClientCertificate() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                .with(jwt().jwt(token -> token.subject("ops-a").claim("scope", "ORDER_READ")))
                .with(trustedCertificate()))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics")
                .with(jwt().jwt(token -> token.subject("ops-a").claim("scope", "OPS_READ")))
                .with(trustedCertificate()))
            .andExpect(status().isOk());
    }

    @Test
    void auditsSensitiveAllowedDecisionsWithCorrelationAndWithoutPrincipalData(CapturedOutput output)
        throws Exception {
        mockMvc.perform(get("/orders")
                .with(jwt().jwt(token -> token.subject("private-principal").claim("scope", "ORDER_READ")))
                .with(trustedCertificate())
                .header("X-Correlation-Id", "audit-correlation-42"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/orders")
                .with(jwt().jwt(token -> token.subject("private-principal").claim("scope", "ORDER_READ")))
                .header("X-Correlation-Id", "audit-denied-9"))
            .andExpect(status().isForbidden());

        assertThat(output.getOut())
            .contains("security.authorization decision=allow permission=SCOPE_ORDER_READ "
                    + "route=orders.read correlationId=audit-correlation-42",
                "security.client_certificate decision=deny route=orders.read correlationId=audit-denied-9")
            .doesNotContain("private-principal");
    }

    @Test
    void mapsEveryApplicationControllerRouteAndKeepsSecurityHeadersOnDenials() throws Exception {
        var routes = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
            .flatMap(info -> info.getPatternValues().stream())
            .collect(java.util.stream.Collectors.toSet());

        assertThat(routes).containsExactlyInAnyOrder("/orders", "/api/chain/status", "/error");

        mockMvc.perform(get("/orders"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
            .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

    @Test
    void ordersTheBearerAndCertificateFiltersBeforeAuthorization() {
        List<jakarta.servlet.Filter> filters = springSecurityFilterChain.getFilters("/orders");
        int cipherIndex = indexOf(filters, CipherValidationFilter.class);
        int bearerIndex = indexOf(filters, BearerTokenAuthenticationFilter.class);
        int certificateIndex = indexOf(filters, ClientCertificateFilter.class);
        int authorizationIndex = indexOf(filters, AuthorizationFilter.class);

        assertThat(cipherIndex).isGreaterThanOrEqualTo(0);
        assertThat(cipherIndex).isLessThan(bearerIndex);
        assertThat(bearerIndex).isGreaterThanOrEqualTo(0);
        assertThat(certificateIndex).isGreaterThan(bearerIndex);
        assertThat(authorizationIndex).isGreaterThan(certificateIndex);
        assertThat(applicationContext.containsBean("csrfTokenController")).isFalse();
    }

    @Test
    void enforcesTenantEntitlementsBeforeSyntheticAssetOperations() {
        when(entitlementRepository.existsByPrincipalIdAndTenantIdAndAssetIdAndPermission(
            "user-a", "tenant-a", "asset-a", AssetPermission.READ
        )).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(jwtAuthentication("user-a", "tenant-a"));
        try {
            assertThat(syntheticAssetOperation.read("tenant-a", "asset-a")).isEqualTo("asset-a");
            assertThatThrownBy(() -> syntheticAssetOperation.read("tenant-b", "asset-a"))
                .isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private RequestPostProcessor trustedCertificate() {
        X509Certificate certificate = Mockito.mock(X509Certificate.class);
        when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal("CN=test-client"));
        return request -> {
            request.setAttribute("jakarta.servlet.request.X509Certificate",
                new X509Certificate[] {certificate});
            return request;
        };
    }

    private int indexOf(List<jakarta.servlet.Filter> filters, Class<?> filterType) {
        for (int index = 0; index < filters.size(); index++) {
            if (filterType.isInstance(filters.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private JwtAuthenticationToken jwtAuthentication(String subject, String tenant) {
        Instant now = Instant.now();
        Jwt token = Jwt.withTokenValue("synthetic-test-token")
            .header("alg", "RS256")
            .subject(subject)
            .claim("tenant_id", tenant)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .build();
        return new JwtAuthenticationToken(token, List.of());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SyntheticAssetConfiguration {
        @Bean
        SyntheticAssetOperation syntheticAssetOperation(AssetEntitlementGuard guard) {
            return new SyntheticAssetOperation(guard);
        }
    }

    static class SyntheticAssetOperation {
        private final AssetEntitlementGuard guard;

        SyntheticAssetOperation(AssetEntitlementGuard guard) {
            this.guard = guard;
        }

        String read(String tenantId, String assetId) {
            guard.requirePermission(tenantId, assetId, AssetPermission.READ);
            return assetId;
        }
    }
}
