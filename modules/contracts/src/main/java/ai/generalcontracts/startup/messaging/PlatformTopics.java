package ai.generalcontracts.startup.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class PlatformTopics {
    public static final String CHAIN_EVENTS_RAW = "chain.events.raw";
    public static final String CHAIN_TX_SUBMITTED = "chain.tx.submitted";
    public static final String CHAIN_TX_CONFIRMED = "chain.tx.confirmed";
    public static final String CHAIN_TX_FAILED = "chain.tx.failed";
    public static final String CHAIN_TX_REORGED = "chain.tx.reorged";
    public static final String SETTLEMENT_EVENTS = "settlement.events";
    public static final String TOKENIZATION_EVENTS = "tokenization.events";
    public static final String TRANSFER_EVENTS = "transfer.events";
    public static final String COMPLIANCE_EVENTS = "compliance.events";
    public static final String BLOCKCHAIN_EVENTS = "blockchain.events";

    @Bean NewTopic chainEventsRawTopic() { return topic(CHAIN_EVENTS_RAW); }
    @Bean NewTopic chainTxSubmittedTopic() { return topic(CHAIN_TX_SUBMITTED); }
    @Bean NewTopic chainTxConfirmedTopic() { return topic(CHAIN_TX_CONFIRMED); }
    @Bean NewTopic chainTxFailedTopic() { return topic(CHAIN_TX_FAILED); }
    @Bean NewTopic chainTxReorgedTopic() { return topic(CHAIN_TX_REORGED); }
    @Bean NewTopic settlementEventsTopic() { return topic(SETTLEMENT_EVENTS); }
    @Bean NewTopic tokenizationEventsTopic() { return topic(TOKENIZATION_EVENTS); }
    @Bean NewTopic transferEventsTopic() { return topic(TRANSFER_EVENTS); }
    @Bean NewTopic complianceEventsTopic() { return topic(COMPLIANCE_EVENTS); }
    @Bean NewTopic blockchainEventsTopic() { return topic(BLOCKCHAIN_EVENTS); }
    @Bean NewTopic chainEventsRawDltTopic() { return topic(CHAIN_EVENTS_RAW + ".dlt"); }
    @Bean NewTopic chainTxSubmittedDltTopic() { return topic(CHAIN_TX_SUBMITTED + ".dlt"); }
    @Bean NewTopic chainTxConfirmedDltTopic() { return topic(CHAIN_TX_CONFIRMED + ".dlt"); }
    @Bean NewTopic chainTxFailedDltTopic() { return topic(CHAIN_TX_FAILED + ".dlt"); }
    @Bean NewTopic chainTxReorgedDltTopic() { return topic(CHAIN_TX_REORGED + ".dlt"); }
    @Bean NewTopic settlementEventsDltTopic() { return topic(SETTLEMENT_EVENTS + ".dlt"); }
    @Bean NewTopic tokenizationEventsDltTopic() { return topic(TOKENIZATION_EVENTS + ".dlt"); }
    @Bean NewTopic transferEventsDltTopic() { return topic(TRANSFER_EVENTS + ".dlt"); }
    @Bean NewTopic complianceEventsDltTopic() { return topic(COMPLIANCE_EVENTS + ".dlt"); }
    @Bean NewTopic blockchainEventsDltTopic() { return topic(BLOCKCHAIN_EVENTS + ".dlt"); }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(1).replicas(1).build();
    }
}
