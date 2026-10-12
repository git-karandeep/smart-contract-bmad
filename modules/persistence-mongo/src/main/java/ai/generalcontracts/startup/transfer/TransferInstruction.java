package ai.generalcontracts.startup.transfer;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "transfer_instructions")
@CompoundIndex(
    name = "transfer_principal_idempotency_unique",
    def = "{'tenantId': 1, 'initiatingPrincipal': 1, 'idempotencyKey': 1}",
    unique = true
)
public class TransferInstruction {
    @Id
    private String instructionId;
    private String tenantId;
    private String initiatingPrincipal;
    private String idempotencyKey;
    private String requestFingerprint;
    private String sourceWallet;
    private String destinationWallet;
    private String tokenIdentifier;
    private String amount;
    private long networkId;
    private TransferStatus status;
    private WorkflowLaunchState workflowLaunchState;
    private String reasonCode;
    private String correlationId;
    private String transactionHash;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    public TransferInstruction() {
    }

    public String getInstructionId() { return instructionId; }
    public void setInstructionId(String instructionId) { this.instructionId = instructionId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getInitiatingPrincipal() { return initiatingPrincipal; }
    public void setInitiatingPrincipal(String initiatingPrincipal) { this.initiatingPrincipal = initiatingPrincipal; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
    public String getSourceWallet() { return sourceWallet; }
    public void setSourceWallet(String sourceWallet) { this.sourceWallet = sourceWallet; }
    public String getDestinationWallet() { return destinationWallet; }
    public void setDestinationWallet(String destinationWallet) { this.destinationWallet = destinationWallet; }
    public String getTokenIdentifier() { return tokenIdentifier; }
    public void setTokenIdentifier(String tokenIdentifier) { this.tokenIdentifier = tokenIdentifier; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public long getNetworkId() { return networkId; }
    public void setNetworkId(long networkId) { this.networkId = networkId; }
    public TransferStatus getStatus() { return status; }
    public void setStatus(TransferStatus status) { this.status = status; }
    public WorkflowLaunchState getWorkflowLaunchState() { return workflowLaunchState; }
    public void setWorkflowLaunchState(WorkflowLaunchState workflowLaunchState) { this.workflowLaunchState = workflowLaunchState; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getTransactionHash() { return transactionHash; }
    public void setTransactionHash(String transactionHash) { this.transactionHash = transactionHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
