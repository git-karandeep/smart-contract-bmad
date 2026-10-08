package ai.generalcontracts.startup.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;

class KafkaPlatformEventPublisherTest {
    @Test
    void exposesAsynchronousBrokerFailureToCaller() {
        KafkaTemplate<String, PlatformEvent> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, PlatformEvent>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(
            org.mockito.ArgumentMatchers.<ProducerRecord<String, PlatformEvent>>any()
        )).thenReturn(failedSend);

        MDC.put("requestId", "request-1");
        MDC.put("correlationId", "correlation-1");
        MDC.put("transactionId", "transaction-1");
        try {
            CompletableFuture<SendResult<String, PlatformEvent>> result =
                new KafkaPlatformEventPublisher(kafkaTemplate).publish(
                    "settlement.events",
                    new PlatformEvent("event-1", "SettlementCompleted", "business-1", Instant.now(), 31337L, null, null, 1, Map.of())
                );

            assertThat(result).isSameAs(failedSend);
            assertThatThrownBy(result::join)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("broker unavailable");
            verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.<ProducerRecord<String, PlatformEvent>>argThat(record ->
                "request-1".equals(new String(record.headers().lastHeader("X-Request-Id").value(), StandardCharsets.UTF_8))
                    && "correlation-1".equals(new String(record.headers().lastHeader("X-Correlation-Id").value(), StandardCharsets.UTF_8))
                    && "transaction-1".equals(new String(record.headers().lastHeader("X-Transaction-Id").value(), StandardCharsets.UTF_8))
            ));
        } finally {
            MDC.remove("requestId");
            MDC.remove("correlationId");
            MDC.remove("transactionId");
        }
    }

    @Test
    void sanitizesUntrustedValuesInFailureLogs() {
        KafkaTemplate<String, PlatformEvent> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, PlatformEvent>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(
            org.mockito.ArgumentMatchers.<ProducerRecord<String, PlatformEvent>>any()
        )).thenReturn(failedSend);

        Logger logger = (Logger) LoggerFactory.getLogger(KafkaPlatformEventPublisher.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new KafkaPlatformEventPublisher(kafkaTemplate).publish(
                "settlement.events\nforged",
                new PlatformEvent("event-1\rforged", "SettlementCompleted", "business-1", Instant.now(), 31337L, null, null, 1, Map.of())
            );

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage())
                .doesNotContain("\n", "\r")
                .contains("settlement.events?forged", "event-1?forged");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
