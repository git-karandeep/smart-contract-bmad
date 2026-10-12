package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.startup.messaging.PlatformTopics;
import ai.generalcontracts.startup.workflow.ScreeningResult;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class ScreeningResultKafkaConsumer {
    private final ScreeningResultIngestionService ingestion;

    public ScreeningResultKafkaConsumer(ScreeningResultIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @KafkaListener(topics = PlatformTopics.SCREENING_RESULTS, groupId = "transfer-screening-ingestion")
    public void receive(ConsumerRecord<String, ScreeningResult> record) {
        ScreeningResult value = record.value();
        if (value == null) {
            throw new IllegalArgumentException("Screening result message is empty");
        }
        Header signature = record.headers().lastHeader("X-Screening-Signature");
        String encodedSignature = signature == null || signature.value() == null
            ? null
            : new String(signature.value(), StandardCharsets.UTF_8);
        ingestion.ingest(value, encodedSignature);
    }
}
