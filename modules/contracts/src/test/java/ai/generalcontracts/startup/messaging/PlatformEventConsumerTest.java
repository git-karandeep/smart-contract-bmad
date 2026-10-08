package ai.generalcontracts.startup.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PlatformEventConsumerTest {
    @Test
    void toleratesNullHeadersAndSanitizesUntrustedValuesBeforeLogging() {
        Logger logger = (Logger) LoggerFactory.getLogger(PlatformEventConsumer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            PlatformEvent event = new PlatformEvent(
                "event-1\nFORGED",
                "ChainTxConfirmed",
                "business-1",
                Instant.EPOCH,
                31337L,
                "0xabc\rforged",
                1L,
                1,
                Map.of()
            );
            ConsumerRecord<String, PlatformEvent> record =
                new ConsumerRecord<>(PlatformTopics.CHAIN_TX_CONFIRMED, 0, 0L, "business-1", event);
            record.headers().add(new RecordHeader("X-Correlation-Id", null));
            record.headers().add(new RecordHeader(
                "X-Transaction-Id",
                "transaction-1\nforged".getBytes(StandardCharsets.UTF_8)
            ));
            record.headers().add(new RecordHeader(
                "X-Request-Id",
                "request-1\u2028forged".getBytes(StandardCharsets.UTF_8)
            ));

            assertThatCode(() -> new PlatformEventConsumer().onConfirmed(record)).doesNotThrowAnyException();
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage())
                .doesNotContain("\n", "\r")
                .doesNotContain("\u2028")
                .contains("event-1?FORGED", "transaction-1?forged", "request-1?forged", "0xabc?forged");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
