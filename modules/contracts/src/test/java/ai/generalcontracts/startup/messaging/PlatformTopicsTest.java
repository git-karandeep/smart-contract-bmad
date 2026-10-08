package ai.generalcontracts.startup.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformTopicsTest {
    @Test
    void declaresInitialEpicTopics() {
        PlatformTopics topics = new PlatformTopics();

        assertThat(List.of(
            topics.tokenizationEventsTopic().name(),
            topics.transferEventsTopic().name(),
            topics.complianceEventsTopic().name(),
            topics.settlementEventsTopic().name(),
            topics.blockchainEventsTopic().name()
        )).containsExactlyInAnyOrder(
            "tokenization.events",
            "transfer.events",
            "compliance.events",
            "settlement.events",
            "blockchain.events"
        );
    }

    @Test
    void declaresDeadLetterTopicsForEveryDomainEventTopic() {
        PlatformTopics topics = new PlatformTopics();

        assertThat(List.of(
            topics.tokenizationEventsDltTopic().name(),
            topics.transferEventsDltTopic().name(),
            topics.complianceEventsDltTopic().name(),
            topics.settlementEventsDltTopic().name(),
            topics.blockchainEventsDltTopic().name()
        )).containsExactlyInAnyOrder(
            "tokenization.events.dlt",
            "transfer.events.dlt",
            "compliance.events.dlt",
            "settlement.events.dlt",
            "blockchain.events.dlt"
        );
    }
}
