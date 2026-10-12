package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.startup.transfer.TransferInstruction;
import java.util.Optional;

public interface ScreeningRequestPolicyResolver {
    Optional<ScreeningRequestPolicy> resolve(TransferInstruction instruction, ScreeningType screeningType);
}
