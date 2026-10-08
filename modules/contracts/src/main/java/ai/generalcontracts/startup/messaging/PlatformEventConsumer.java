package ai.generalcontracts.startup.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class PlatformEventConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformEventConsumer.class);
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}\\p{Zl}\\p{Zp}]");

    @KafkaListener(topics = PlatformTopics.CHAIN_TX_CONFIRMED, groupId = "settlement-workers")
    public void onConfirmed(ConsumerRecord<String, PlatformEvent> record) {
        PlatformEvent event = record.value();
        String requestId = readHeader(record, "X-Request-Id");
        String correlationId = readHeader(record, "X-Correlation-Id");
        String transactionId = readHeader(record, "X-Transaction-Id");
        LOGGER.info(
            "Received chain confirmation event id={} type={} requestId={} correlationId={} transactionId={} txHash={}",
            safeLogValue(event.eventId()),
            safeLogValue(event.eventType()),
            safeLogValue(requestId),
            safeLogValue(correlationId),
            safeLogValue(transactionId),
            safeLogValue(event.txHash())
        );
    }

    private String readHeader(ConsumerRecord<String, PlatformEvent> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null
            ? null
            : new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String safeLogValue(String value) {
        if (value == null) {
            return null;
        }
        String escaped = CONTROL_CHARACTERS.matcher(value).replaceAll("?");
        return escaped.substring(0, Math.min(escaped.length(), 128));
    }
}
