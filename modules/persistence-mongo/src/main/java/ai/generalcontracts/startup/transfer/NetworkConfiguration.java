package ai.generalcontracts.startup.transfer;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "network_configurations")
public class NetworkConfiguration {
    @Id
    private String id;

    @Indexed(unique = true)
    private long networkId;

    private String networkName;

    private String addressFormat;

    private boolean enabled;

    private String chainId;

    public NetworkConfiguration() {
    }

    public NetworkConfiguration(long networkId, String networkName, String addressFormat, boolean enabled, String chainId) {
        this.networkId = networkId;
        this.networkName = networkName;
        this.addressFormat = addressFormat;
        this.enabled = enabled;
        this.chainId = chainId;
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

    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName;
    }

    public String getAddressFormat() {
        return addressFormat;
    }

    public void setAddressFormat(String addressFormat) {
        this.addressFormat = addressFormat;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChainId() {
        return chainId;
    }

    public void setChainId(String chainId) {
        this.chainId = chainId;
    }
}
