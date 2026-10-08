package ai.generalcontracts.startup.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.SendResult;

class KafkaConsumerConfigurationTest {
    @Test
    void publishesExhaustedRecordsToTheMatchingDeadLetterTopic() {
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<Object, Object>> sent =
            CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(
            org.mockito.ArgumentMatchers.<ProducerRecord<Object, Object>>any()
        )).thenReturn(sent);
        ConsumerRecordRecoverer recoverer = new KafkaConsumerConfiguration().deadLetterRecoverer(kafkaTemplate);
        ConsumerRecord<Object, Object> record =
            new ConsumerRecord<>("chain.tx.confirmed", 0, 12L, "business-1", "event");

        recoverer.accept(record, new IllegalStateException("invalid event"));

        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.<ProducerRecord<Object, Object>>argThat(deadLetterRecord ->
            deadLetterRecord.topic().equals("chain.tx.confirmed.dlt")
                && deadLetterRecord.key().equals("business-1")
                && deadLetterRecord.value().equals("event")
        ));
    }
}
