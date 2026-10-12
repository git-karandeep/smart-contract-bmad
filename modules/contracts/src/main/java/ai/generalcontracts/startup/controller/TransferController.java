package ai.generalcontracts.startup.controller;

import ai.generalcontracts.startup.model.TransferRequest;
import ai.generalcontracts.startup.model.TransferResponse;
import ai.generalcontracts.startup.security.AssetEntitlementGuard;
import ai.generalcontracts.startup.security.AssetPermission;
import ai.generalcontracts.startup.transfer.TransferCreateCommand;
import ai.generalcontracts.startup.transfer.TransferLifecycleService;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import ai.generalcontracts.startup.transfer.TransferWorkflowStarter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/transfers")
@ConditionalOnProperty(name = "app.transfer.api.enabled", havingValue = "true")
public class TransferController {
    private final TransferLifecycleService lifecycle;
    private final AssetEntitlementGuard entitlementGuard;
    private final TransferWorkflowStarter workflowStarter;

    public TransferController(
        TransferLifecycleService lifecycle,
        AssetEntitlementGuard entitlementGuard,
        TransferWorkflowStarter workflowStarter
    ) {
        this.lifecycle = lifecycle;
        this.entitlementGuard = entitlementGuard;
        this.workflowStarter = workflowStarter;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(
        @Valid @RequestBody TransferRequest request,
        JwtAuthenticationToken authentication
    ) {
        String principal = authentication.getToken().getSubject();
        String tenant = authentication.getToken().getClaimAsString("tenant_id");
        if (!StringUtils.hasText(principal) || !StringUtils.hasText(tenant)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A verified tenant principal is required");
        }
        entitlementGuard.requirePermission(tenant, request.sourceWallet(), AssetPermission.WRITE);
        TransferInstruction created = lifecycle.create(new TransferCreateCommand(
            request.sourceWallet(),
            request.destinationWallet(),
            request.tokenIdentifier(),
            request.amount(),
            request.networkId(),
            request.idempotencyKey(),
            tenant,
            principal
        ));
        TransferInstruction started = lifecycle.startWorkflow(created.getInstructionId(), workflowStarter);
        return ResponseEntity.accepted().body(TransferResponse.from(started));
    }

    @GetMapping("/{instructionId}")
    public TransferResponse get(
        @PathVariable String instructionId,
        JwtAuthenticationToken authentication
    ) {
        TransferInstruction instruction = lifecycle.get(instructionId);
        String principal = authentication.getToken().getSubject();
        String tenant = authentication.getToken().getClaimAsString("tenant_id");
        if (!instruction.getInitiatingPrincipal().equals(principal)
            || !instruction.getTenantId().equals(tenant)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer instruction not found");
        }
        entitlementGuard.requirePermission(tenant, instruction.getSourceWallet(), AssetPermission.READ);
        return TransferResponse.from(instruction);
    }
}
