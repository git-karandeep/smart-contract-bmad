package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.chain.listener.ObservedTransferEvent;
import java.util.Optional;

public interface AuthoritativeTransferEventSource {
    Optional<ObservedTransferEvent> findFinalizedTransfer(String instructionId, String transactionHash);
}
