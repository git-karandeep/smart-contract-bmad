package ai.generalcontracts.startup.transfer;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "wallet_eligibility")
@CompoundIndex(
    name = "tenant_wallet_unique",
    def = "{'tenantId': 1, 'walletAddress': 1}",
    unique = true
)
public class WalletEligibility {
    @Id
    private String id;

    @Indexed
    private String tenantId;

    @Indexed
    private String walletAddress;

    private boolean eligible;

    private boolean frozen;

    private String freezeReason;

    public WalletEligibility() {
    }

    public WalletEligibility(String tenantId, String walletAddress, boolean eligible) {
        this.tenantId = tenantId;
        this.walletAddress = walletAddress;
        this.eligible = eligible;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public void setWalletAddress(String walletAddress) {
        this.walletAddress = walletAddress;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public String getFreezeReason() {
        return freezeReason;
    }

    public void setFreezeReason(String freezeReason) {
        this.freezeReason = freezeReason;
    }
}
