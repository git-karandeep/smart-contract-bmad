package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.chain.TransferSubmission;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransferWorkerConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ScreeningRequestPolicyResolver unavailableScreeningPolicyResolver() {
        return (instruction, type) -> Optional.empty();
    }

    @Bean
    @ConditionalOnMissingBean
    ScreeningRequestPublisher unavailableScreeningRequestPublisher() {
        return request -> {
            throw new IllegalStateException("Screening Kafka publication is not configured");
        };
    }

    @Bean
    @ConditionalOnMissingBean
    TransferValidationPolicy unavailableTransferValidationPolicy() {
        return instruction -> TransferValidationPolicy.ValidationDecision.rejected(
            "TRANSFER_VALIDATION_POLICY_UNAVAILABLE"
        );
    }

    @Bean
    @ConditionalOnMissingBean
    PreExecutionPolicy unavailablePreExecutionPolicy() {
        return instruction -> false;
    }

    @Bean
    @ConditionalOnMissingBean(
        value = TransferSubmissionPort.class,
        ignored = Web3jTransferSubmissionAdapter.class
    )
    TransferSubmissionPort unavailableTransferSubmissionPort() {
        return instruction -> new TransferSubmission(
            instruction.getInstructionId(),
            instruction.getNetworkId(),
            instruction.getTokenIdentifier(),
            instruction.getSourceWallet(),
            instruction.getDestinationWallet(),
            instruction.getAmount(),
            null,
            TransferSubmission.Outcome.UNKNOWN,
            "TRANSFER_SUBMISSION_INTEGRATION_UNAVAILABLE"
        );
    }

    @Bean
    @ConditionalOnMissingBean
    TokenDecimalsPolicy unavailableTokenDecimalsPolicy() {
        return (networkId, tokenContract) -> OptionalInt.empty();
    }

    @Bean
    @ConditionalOnMissingBean
    AuthoritativeTransferEventSource unavailableAuthoritativeTransferEventSource() {
        return (instructionId, transactionHash) -> Optional.empty();
    }

}
