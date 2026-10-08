package ai.generalcontracts.startup.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

class KafkaMessagingTest {
    private static final String TOPIC = "settlement.events";

    private EmbeddedKafkaKraftBroker broker;
    private KafkaTemplate<String, PlatformEvent> kafkaTemplate;
    private KafkaConsumer<String, PlatformEvent> consumer;

    @BeforeEach
    void startBrokerAndClients() {
        broker = new EmbeddedKafkaKraftBroker(1, 1, TOPIC);
        broker.afterPropertiesSet();
        String bootstrapServers = broker.getBrokersAsString();

        Map<String, Object> producerProperties = Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ProducerConfig.ACKS_CONFIG, "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true
        );
        kafkaTemplate = new KafkaTemplate<>(
            new DefaultKafkaProducerFactory<>(producerProperties, new StringSerializer(), new JsonSerializer<>())
        );

        Map<String, Object> consumerProperties = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG, "messaging-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
        );
        consumer = new KafkaConsumer<>(
            consumerProperties,
            new StringDeserializer(),
            new JsonDeserializer<>(PlatformEvent.class, false)
        );
        consumer.subscribe(List.of(TOPIC));
    }

    @AfterEach
    void stopBrokerAndClients() {
        if (consumer != null) {
            consumer.close();
        }
        if (kafkaTemplate != null) {
            kafkaTemplate.destroy();
        }
        if (broker != null) {
            broker.destroy();
        }
    }

    @Test
    @Tag("integration")
    void serializesAndConsumesPlatformEvents() throws Exception {
        PlatformEvent event = new PlatformEvent(
            "event-17",
            "SettlementCompleted",
            "business-17",
            Instant.parse("2026-10-07T00:00:00Z"),
            31337L,
            "0xabc",
            17L,
            1,
            Map.of("result", "settled")
        );
        MDC.put("requestId", "request-17");
        MDC.put("correlationId", "correlation-17");
        MDC.put("transactionId", "transaction-17");
        try {
            new KafkaPlatformEventPublisher(kafkaTemplate).publish(TOPIC, event);
        } finally {
            MDC.remove("requestId");
            MDC.remove("correlationId");
            MDC.remove("transactionId");
        }

        ConsumerRecord<String, PlatformEvent> received = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (received == null && System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(250));
            if (!records.isEmpty()) {
                received = records.iterator().next();
            }
        }

        assertThat(received).isNotNull();
        assertThat(received.key()).isEqualTo("business-17");
        assertThat(received.value()).isEqualTo(event);
        assertThat(received.headers().lastHeader("X-Request-Id").value())
            .isEqualTo("request-17".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(received.headers().lastHeader("X-Correlation-Id").value())
            .isEqualTo("correlation-17".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(received.headers().lastHeader("X-Transaction-Id").value())
            .isEqualTo("transaction-17".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
