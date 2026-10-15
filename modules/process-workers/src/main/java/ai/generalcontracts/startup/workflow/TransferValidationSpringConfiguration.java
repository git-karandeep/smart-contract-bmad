package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.startup.transfer.NetworkConfigurationRepository;
import ai.generalcontracts.startup.transfer.TokenConfigurationRepository;
import ai.generalcontracts.startup.transfer.WalletEligibilityRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.transfer.validation.enabled", havingValue = "true", matchIfMissing = true)
public class TransferValidationSpringConfiguration {
    @Bean
    public TransferValidationPolicy transferValidationPolicy(
        NetworkConfigurationRepository networkConfigRepository,
        TokenConfigurationRepository tokenConfigRepository,
        WalletEligibilityRepository walletEligibilityRepository
    ) {
        return new TransferValidationServiceImpl(
            networkConfigRepository,
            tokenConfigRepository,
            walletEligibilityRepository
        );
    }
}
