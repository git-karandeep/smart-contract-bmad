package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.startup.messaging.PlatformTopics;
import ai.generalcontracts.startup.workflow.ScreeningRequestMessage;
import ai.generalcontracts.startup.workflow.ScreeningRequestPublisher;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaScreeningRequestPublisher implements ScreeningRequestPublisher {
    private final KafkaTemplate<String, ScreeningRequestMessage> kafkaTemplate;

    public KafkaScreeningRequestPublisher(KafkaTemplate<String, ScreeningRequestMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(ScreeningRequestMessage request) {
        ProducerRecord<String, ScreeningRequestMessage> record = new ProducerRecord<>(
            PlatformTopics.SCREENING_REQUESTS,
            request.screeningRequestId(),
            request
        );
        record.headers().add(
            "X-Screening-Request-Id",
            request.screeningRequestId().getBytes(StandardCharsets.UTF_8)
        );
        kafkaTemplate.send(record).join();
    }
}
