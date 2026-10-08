package ai.generalcontracts.startup.messaging;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaConsumerConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerConfiguration.class);

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        return new DefaultErrorHandler(
            deadLetterRecoverer(kafkaTemplate),
            new FixedBackOff(1_000L, 2L)
        );
    }

    ConsumerRecordRecoverer deadLetterRecoverer(KafkaTemplate<?, ?> kafkaTemplate) {
        DeadLetterPublishingRecoverer deadLetterPublisher = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) -> new TopicPartition(record.topic() + ".dlt", record.partition())
        );
        deadLetterPublisher.setFailIfSendResultIsError(true);
        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            LOGGER.error(
                "Publishing Kafka record to dead-letter topic after bounded retries topic={} partition={} offset={} failure={}",
                record.topic(),
                record.partition(),
                record.offset(),
                exception.getClass().getSimpleName()
            );
            deadLetterPublisher.accept(record, exception);
        };
        return recoverer;
    }
}
