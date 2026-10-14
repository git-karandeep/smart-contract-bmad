package ai.generalcontracts.startup.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.generalcontracts.startup.model.TransferRequest;
import ai.generalcontracts.startup.security.AssetEntitlementGuard;
import ai.generalcontracts.startup.security.AssetPermission;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import ai.generalcontracts.startup.transfer.TransferLifecycleService;
import ai.generalcontracts.startup.transfer.TransferStatus;
import ai.generalcontracts.startup.transfer.TransferWorkflowStarter;
import ai.generalcontracts.startup.transfer.WorkflowLaunchState;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@Tag("api")
class TransferControllerTest {
    private final TransferLifecycleService lifecycle = mock(TransferLifecycleService.class);
    private final AssetEntitlementGuard entitlementGuard = mock(AssetEntitlementGuard.class);
    private final TransferWorkflowStarter workflowStarter = mock(TransferWorkflowStarter.class);
    private final TransferController controller =
        new TransferController(lifecycle, entitlementGuard, workflowStarter);

    @Test
    void createsUnderAuthenticatedPrincipalAfterSourceWalletEntitlementCheck() {
        TransferInstruction instruction = instruction();
        when(lifecycle.create(org.mockito.ArgumentMatchers.any())).thenReturn(instruction);
        when(lifecycle.startWorkflow("transfer-1", workflowStarter)).thenReturn(instruction);

        var response = controller.create(request(), authentication());

        assertEquals(202, response.getStatusCode().value());
        assertEquals("transfer-1", response.getBody().instructionId());
        verify(entitlementGuard).requirePermission("tenant-1", instruction.getSourceWallet(), AssetPermission.WRITE);
        verify(lifecycle).startWorkflow("transfer-1", workflowStarter);
    }

    @Test
    void rejectsMissingVerifiedTenantBeforePersisting() {
        Jwt token = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject("principal-1")
            .issuedAt(Instant.parse("2026-10-11T20:00:00Z"))
            .expiresAt(Instant.parse("2026-10-11T21:00:00Z"))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(token);

        assertThrows(ResponseStatusException.class, () -> controller.create(request(), authentication));
        verify(lifecycle, never()).create(org.mockito.ArgumentMatchers.any());
        verify(entitlementGuard, never()).requirePermission(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void returnsStatusOnlyForTheInitiatingPrincipalAndTenantAfterReadEntitlement() {
        TransferInstruction instruction = instruction();
        instruction.setInitiatingPrincipal("principal-1");
        instruction.setTenantId("tenant-1");
        when(lifecycle.get("transfer-1")).thenReturn(instruction);

        assertEquals(TransferStatus.RECEIVED, controller.get("transfer-1", authentication()).status());
        verify(entitlementGuard).requirePermission("tenant-1", instruction.getSourceWallet(), AssetPermission.READ);

        instruction.setInitiatingPrincipal("someone-else");
        assertThrows(ResponseStatusException.class, () -> controller.get("transfer-1", authentication()));
    }

    private TransferRequest request() {
        return new TransferRequest(
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222",
            "0x3333333333333333333333333333333333333333",
            "10.25", 1337L, "request-key"
        );
    }

    private JwtAuthenticationToken authentication() {
        Jwt token = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject("principal-1")
            .claim("tenant_id", "tenant-1")
            .issuedAt(Instant.parse("2026-10-11T20:00:00Z"))
            .expiresAt(Instant.parse("2026-10-11T21:00:00Z"))
            .build();
        return new JwtAuthenticationToken(token);
    }

    private TransferInstruction instruction() {
        TransferInstruction instruction = new TransferInstruction();
        instruction.setInstructionId("transfer-1");
        instruction.setSourceWallet("0x1111111111111111111111111111111111111111");
        instruction.setDestinationWallet("0x2222222222222222222222222222222222222222");
        instruction.setTokenIdentifier("0x3333333333333333333333333333333333333333");
        instruction.setAmount("10.25");
        instruction.setNetworkId(1337L);
        instruction.setStatus(TransferStatus.RECEIVED);
        instruction.setWorkflowLaunchState(WorkflowLaunchState.STARTED);
        instruction.setCreatedAt(Instant.parse("2026-10-11T20:00:00Z"));
        instruction.setUpdatedAt(Instant.parse("2026-10-11T20:00:00Z"));
        return instruction;
    }
}
