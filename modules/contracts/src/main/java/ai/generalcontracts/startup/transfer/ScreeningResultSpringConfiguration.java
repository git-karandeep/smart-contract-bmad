package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.startup.workflow.HmacScreeningResultAuthenticator;
import ai.generalcontracts.startup.workflow.ProviderSecretResolver;
import ai.generalcontracts.startup.workflow.ScreeningResultAuthenticator;
import ai.generalcontracts.startup.workflow.ScreeningResultValidator;
import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScreeningResultSpringConfiguration {
    @Bean
    ScreeningResultAuthenticator screeningResultAuthenticator(ProviderSecretResolver secrets) {
        return new HmacScreeningResultAuthenticator(secrets);
    }

    @Bean
    ScreeningResultValidator screeningResultValidator() {
        return new ScreeningResultValidator();
    }

    @Bean
    @ConditionalOnBean(ZeebeClient.class)
    ScreeningWorkflowCorrelator camundaScreeningWorkflowCorrelator(ZeebeClient client) {
        return new CamundaScreeningWorkflowCorrelator(client);
    }

    @Bean
    @ConditionalOnMissingBean(ScreeningWorkflowCorrelator.class)
    ScreeningWorkflowCorrelator unavailableScreeningWorkflowCorrelator() {
        return result -> {
            throw new IllegalStateException("Workflow message correlation is not configured");
        };
    }

    @Bean
    @ConditionalOnBean(ZeebeClient.class)
    TransferWorkflowStarter camundaTransferWorkflowStarter(ZeebeClient client) {
        return new CamundaTransferWorkflowStarter(client);
    }

    @Bean
    @ConditionalOnMissingBean(TransferWorkflowStarter.class)
    TransferWorkflowStarter unavailableTransferWorkflowStarter() {
        return instruction -> {
            throw new IllegalStateException("Transfer workflow start is not configured");
        };
    }
}
