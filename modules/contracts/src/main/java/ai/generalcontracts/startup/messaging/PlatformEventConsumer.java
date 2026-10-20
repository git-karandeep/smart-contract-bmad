package ai.generalcontracts.startup.messaging;

import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class PlatformEventConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformEventConsumer.class);
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}\\p{Zl}\\p{Zp}]");
    private final ZeebeClient client;

    public PlatformEventConsumer(ZeebeClient client) {
        this.client = client;
    }

    @KafkaListener(
        topics = {
            PlatformTopics.CHAIN_TX_SUBMITTED,
            PlatformTopics.CHAIN_TX_CONFIRMED,
            PlatformTopics.CHAIN_TX_FAILED,
            PlatformTopics.CHAIN_TX_REORGED
        },
        groupId = "settlement-workers"
    )
    public void onTransaction(ConsumerRecord<String, PlatformEvent> record) {
        PlatformEvent event = record.value();
        if (event == null) {
            throw new IllegalArgumentException("Chain transaction event is empty");
        }
        String requestId = readHeader(record, "X-Request-Id");
        String correlationId = readHeader(record, "X-Correlation-Id");
        String transactionId = readHeader(record, "X-Transaction-Id");
        LOGGER.info(
            "Received chain transaction event id={} type={} requestId={} correlationId={} transactionId={} txHash={}",
            safeLogValue(event.eventId()),
            safeLogValue(event.eventType()),
            safeLogValue(requestId),
            safeLogValue(correlationId),
            safeLogValue(transactionId),
            safeLogValue(event.txHash())
        );
        String instructionId = required(event.businessId(), "businessId");
        String transactionHash = required(event.txHash(), "txHash");
        String eventId = required(event.eventId(), "eventId");
        TransactionResult result = transactionResult(record.topic());
        client.newPublishMessageCommand()
            .messageName("TransferTransactionResult")
            .correlationKey(instructionId)
            .messageId("chain-transaction-result:" + result.outcome() + ":" + eventId)
            .timeToLive(Duration.ofMinutes(15))
            .variables(Map.of(
                "instructionId", instructionId,
                "transactionHash", transactionHash,
                "transactionOutcome", result.outcome(),
                "finalized", result.finalized()
            ))
            .send()
            .join();
    }

    private TransactionResult transactionResult(String topic) {
        if (PlatformTopics.CHAIN_TX_SUBMITTED.equals(topic)) {
            return new TransactionResult("PENDING", false);
        }
        if (PlatformTopics.CHAIN_TX_CONFIRMED.equals(topic)) {
            return new TransactionResult("CONFIRMED", true);
        }
        if (PlatformTopics.CHAIN_TX_FAILED.equals(topic)) {
            return new TransactionResult("REVERTED", true);
        }
        if (PlatformTopics.CHAIN_TX_REORGED.equals(topic)) {
            return new TransactionResult("UNKNOWN", false);
        }
        throw new IllegalArgumentException("Unsupported chain transaction topic: " + topic);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Chain transaction event is missing " + field);
        }
        return value;
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

    private record TransactionResult(String outcome, boolean finalized) {
    }
}
