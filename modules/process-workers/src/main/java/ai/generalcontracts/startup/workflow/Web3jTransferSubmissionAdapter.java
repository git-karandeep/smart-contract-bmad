package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.chain.Erc20TransferCommand;
import ai.generalcontracts.chain.TransferSubmission;
import ai.generalcontracts.chain.Web3jErc20TransferAdapter;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(Web3jErc20TransferAdapter.class)
@Primary
public class Web3jTransferSubmissionAdapter implements TransferSubmissionPort {
    private final Web3jErc20TransferAdapter adapter;

    public Web3jTransferSubmissionAdapter(Web3jErc20TransferAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public TransferSubmission submit(TransferInstruction instruction) {
        return adapter.submit(new Erc20TransferCommand(
            instruction.getInstructionId(),
            instruction.getNetworkId(),
            instruction.getTokenIdentifier(),
            instruction.getSourceWallet(),
            instruction.getDestinationWallet(),
            instruction.getAmount()
        ));
    }

    @Override
    public Optional<TransferSubmission> findJournaledSubmission(String instructionId) {
        return adapter.findJournaledSubmission(instructionId);
    }
}
