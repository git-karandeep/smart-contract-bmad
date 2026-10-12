package ai.generalcontracts.startup.workflow;

import java.util.OptionalInt;

public interface TokenDecimalsPolicy {
    OptionalInt decimals(long networkId, String tokenContract);
}
