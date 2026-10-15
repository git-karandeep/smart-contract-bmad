package ai.generalcontracts.startup.transfer;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "token_configurations")
@CompoundIndex(
    name = "network_token_unique",
    def = "{'networkId': 1, 'tokenIdentifier': 1}",
    unique = true
)
public class TokenConfiguration {
    @Id
    private String id;

    @Indexed
    private long networkId;

    @Indexed
    private String tokenIdentifier;

    private String tokenName;

    private String tokenSymbol;

    private int decimals;

    private boolean enabled;

    private boolean allowSelfTransfer;

    private String minTransferAmount;

    private String maxTransferAmount;

    public TokenConfiguration() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(long networkId) {
        this.networkId = networkId;
    }

    public String getTokenIdentifier() {
        return tokenIdentifier;
    }

    public void setTokenIdentifier(String tokenIdentifier) {
        this.tokenIdentifier = tokenIdentifier;
    }

    public String getTokenName() {
        return tokenName;
    }

    public void setTokenName(String tokenName) {
        this.tokenName = tokenName;
    }

    public String getTokenSymbol() {
        return tokenSymbol;
    }

    public void setTokenSymbol(String tokenSymbol) {
        this.tokenSymbol = tokenSymbol;
    }

    public int getDecimals() {
        return decimals;
    }

    public void setDecimals(int decimals) {
        this.decimals = decimals;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAllowSelfTransfer() {
        return allowSelfTransfer;
    }

    public void setAllowSelfTransfer(boolean allowSelfTransfer) {
        this.allowSelfTransfer = allowSelfTransfer;
    }

    public String getMinTransferAmount() {
        return minTransferAmount;
    }

    public void setMinTransferAmount(String minTransferAmount) {
        this.minTransferAmount = minTransferAmount;
    }

    public String getMaxTransferAmount() {
        return maxTransferAmount;
    }

    public void setMaxTransferAmount(String maxTransferAmount) {
        this.maxTransferAmount = maxTransferAmount;
    }
}
