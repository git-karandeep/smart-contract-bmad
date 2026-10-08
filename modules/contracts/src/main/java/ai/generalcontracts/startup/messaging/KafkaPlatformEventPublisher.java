package ai.generalcontracts.startup.messaging;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaPlatformEventPublisher implements PlatformEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPlatformEventPublisher.class);
    private static final Pattern LOG_CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}\\p{Zl}\\p{Zp}]");
    private final KafkaTemplate<String, PlatformEvent> kafkaTemplate;

    public KafkaPlatformEventPublisher(KafkaTemplate<String, PlatformEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public CompletableFuture<SendResult<String, PlatformEvent>> publish(String topic, PlatformEvent event) {
        ProducerRecord<String, PlatformEvent> record = new ProducerRecord<>(topic, event.businessId(), event);
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
        }
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            record.headers().add("X-Request-Id", requestId.getBytes(StandardCharsets.UTF_8));
        }
        String transactionId = MDC.get("transactionId");
        if (transactionId != null) {
            record.headers().add("X-Transaction-Id", transactionId.getBytes(StandardCharsets.UTF_8));
        }
        CompletableFuture<SendResult<String, PlatformEvent>> result = kafkaTemplate.send(record);
        result.whenComplete((sent, failure) -> {
            if (failure != null) {
                LOGGER.error(
                    "Failed to publish platform event topic={} eventId={} failure={}",
                    safeLogValue(topic),
                    safeLogValue(event.eventId()),
                    failure.getClass().getSimpleName()
                );
            }
        });
        return result;
    }

    private String safeLogValue(String value) {
        if (value == null) {
            return null;
        }
        String escaped = LOG_CONTROL_CHARACTERS.matcher(value).replaceAll("?");
        return escaped.substring(0, Math.min(escaped.length(), 128));
    }
}
