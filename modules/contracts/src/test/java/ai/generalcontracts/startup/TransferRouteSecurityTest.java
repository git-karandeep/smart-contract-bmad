package ai.generalcontracts.startup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.generalcontracts.startup.observability.RequestCorrelationFilter;
import ai.generalcontracts.startup.repository.AssetEntitlementRepository;
import ai.generalcontracts.startup.security.AssetPermission;
import ai.generalcontracts.startup.service.OrderService;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import ai.generalcontracts.startup.transfer.TransferLifecycleService;
import ai.generalcontracts.startup.transfer.TransferStatus;
import ai.generalcontracts.startup.transfer.TransferWorkflowStarter;
import ai.generalcontracts.startup.transfer.WorkflowLaunchState;
import java.security.cert.X509Certificate;
import java.time.Instant;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    properties = {
        "app.transfer.api.enabled=true",
        "management.health.mongo.enabled=false",
        "spring.mongodb.uri=mongodb://127.0.0.1:1/health-test?directConnection=true",
        "spring.mongodb.ssl.enabled=false"
    }
)
@ActiveProfiles("test")
class TransferRouteSecurityTest {
    private static final String WALLET = "0x1111111111111111111111111111111111111111";
    private static final String BODY = "{\"sourceWallet\":\"" + WALLET
        + "\",\"destinationWallet\":\"0x2222222222222222222222222222222222222222\","
        + "\"tokenIdentifier\":\"0x3333333333333333333333333333333333333333\","
        + "\"amount\":\"10.25\",\"networkId\":1337,\"idempotencyKey\":\"key-1\"}";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private AssetEntitlementRepository entitlementRepository;

    @MockitoBean
    private TransferLifecycleService lifecycle;

    @MockitoBean
    private TransferWorkflowStarter workflowStarter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilters(applicationContext.getBean(RequestCorrelationFilter.class))
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    @Test
    void deniesUnauthenticatedAndNonJwtCallersOnTransferRoutes() throws Exception {
        mockMvc.perform(post("/api/transfers").contentType("application/json").content(BODY))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/transfers/transfer-1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/transfers").with(user("basic")).with(trustedCertificate())
                .contentType("application/json").content(BODY))
            .andExpect(status().isForbidden());
        verify(lifecycle, never()).create(any());
    }

    @Test
    void deniesJwtCallersWithoutAClientCertificateOnTransferRoutes() throws Exception {
        mockMvc.perform(post("/api/transfers").with(transferJwt())
                .contentType("application/json").content(BODY))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/transfers/transfer-1").with(transferJwt()))
            .andExpect(status().isForbidden());
        verify(lifecycle, never()).create(any());
        verify(lifecycle, never()).get("transfer-1");
    }

    @Test
    void rejectsSourceWalletWithoutEntitlementBeforePersisting() throws Exception {
        mockMvc.perform(post("/api/transfers").with(transferJwt()).with(trustedCertificate())
                .contentType("application/json").content(BODY))
            .andExpect(status().isForbidden());
        verify(lifecycle, never()).create(any());
    }

    @Test
    void returnsClientErrorForInvalidAmountWithoutPersisting() throws Exception {
        mockMvc.perform(post("/api/transfers").with(transferJwt()).with(trustedCertificate())
                .contentType("application/json").content(BODY.replace("10.25", "1e3")))
            .andExpect(status().isBadRequest());
        verify(lifecycle, never()).create(any());
    }

    @Test
    void createsAndReadsInstructionForEntitledJwtPrincipalWithoutIdentityData() throws Exception {
        for (AssetPermission permission : new AssetPermission[] {AssetPermission.READ, AssetPermission.WRITE}) {
            when(entitlementRepository.existsByPrincipalIdAndTenantIdAndAssetIdAndPermission(
                "principal-1", "tenant-1", WALLET, permission)).thenReturn(true);
        }
        TransferInstruction instruction = instruction();
        when(lifecycle.create(any())).thenReturn(instruction);
        when(lifecycle.startWorkflow("transfer-1", workflowStarter)).thenReturn(instruction);
        when(lifecycle.get("transfer-1")).thenReturn(instruction);

        mockMvc.perform(post("/api/transfers").with(transferJwt()).with(trustedCertificate())
                .contentType("application/json").content(BODY))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.instructionId").value("transfer-1"))
            .andExpect(jsonPath("$.status").value("RECEIVED"));
        mockMvc.perform(get("/api/transfers/transfer-1").with(transferJwt()).with(trustedCertificate()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value("10.25"))
            .andExpect(jsonPath("$.initiatingPrincipal").doesNotExist())
            .andExpect(jsonPath("$.tenantId").doesNotExist());
    }

    @Test
    void keepsOtherRoutesDenied() throws Exception {
        mockMvc.perform(get("/api/transfers/transfer-1/extra").with(transferJwt()).with(trustedCertificate()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/transfers").with(transferJwt()).with(trustedCertificate()))
            .andExpect(status().isForbidden());
    }

    private RequestPostProcessor transferJwt() {
        return jwt().jwt(token -> token.subject("principal-1").claim("tenant_id", "tenant-1"));
    }

    private RequestPostProcessor trustedCertificate() {
        X509Certificate certificate = Mockito.mock(X509Certificate.class);
        when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal("CN=test-client"));
        return request -> {
            request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[] {certificate});
            return request;
        };
    }

    private TransferInstruction instruction() {
        TransferInstruction instruction = new TransferInstruction();
        instruction.setInstructionId("transfer-1");
        instruction.setTenantId("tenant-1");
        instruction.setInitiatingPrincipal("principal-1");
        instruction.setSourceWallet(WALLET);
        instruction.setDestinationWallet("0x2222222222222222222222222222222222222222");
        instruction.setTokenIdentifier("0x3333333333333333333333333333333333333333");
        instruction.setAmount("10.25");
        instruction.setNetworkId(1337L);
        instruction.setStatus(TransferStatus.RECEIVED);
        instruction.setWorkflowLaunchState(WorkflowLaunchState.STARTED);
        instruction.setCreatedAt(Instant.parse("2026-10-14T20:00:00Z"));
        instruction.setUpdatedAt(Instant.parse("2026-10-14T20:00:00Z"));
        return instruction;
    }
}
