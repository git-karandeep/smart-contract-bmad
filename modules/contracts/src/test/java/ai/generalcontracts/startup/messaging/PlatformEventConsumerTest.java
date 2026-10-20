package ai.generalcontracts.startup.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.camunda.zeebe.client.ZeebeClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class PlatformEventConsumerTest {
    @Test
    void toleratesNullHeadersAndSanitizesUntrustedValuesBeforeLogging() {
        Logger logger = (Logger) LoggerFactory.getLogger(PlatformEventConsumer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ZeebeClient client = mock(ZeebeClient.class, RETURNS_DEEP_STUBS);
            var command = client.newPublishMessageCommand()
                .messageName("TransferTransactionResult")
                .correlationKey("transfer-1")
                .messageId("chain-transaction-result:CONFIRMED:event-1");
            doAnswer(invocation -> invocation.getMock())
                .when(command).timeToLive(Duration.ofMinutes(15));
            PlatformEvent event = new PlatformEvent(
                "event-1",
                "ChainTxConfirmed",
                "transfer-1",
                Instant.EPOCH,
                31337L,
                "0xabc",
                1L,
                1,
                Map.of()
            );
            ConsumerRecord<String, PlatformEvent> record =
                new ConsumerRecord<>(PlatformTopics.CHAIN_TX_CONFIRMED, 0, 0L, "transfer-1", event);
            record.headers().add(new RecordHeader("X-Correlation-Id", null));
            record.headers().add(new RecordHeader(
                "X-Transaction-Id",
                "transaction-1\nforged".getBytes(StandardCharsets.UTF_8)
            ));
            record.headers().add(new RecordHeader(
                "X-Request-Id",
                "request-1\u2028forged".getBytes(StandardCharsets.UTF_8)
            ));

            assertThatCode(() -> new PlatformEventConsumer(client).onTransaction(record)).doesNotThrowAnyException();
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage())
                .doesNotContain("\n", "\r")
                .doesNotContain("\u2028")
                .contains("event-1", "transaction-1?forged", "request-1?forged", "0xabc");
            verify(command).timeToLive(Duration.ofMinutes(15));
            verify(command).variables(Map.of(
                "instructionId", "transfer-1",
                "transactionHash", "0xabc",
                "transactionOutcome", "CONFIRMED",
                "finalized", true
            ));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void publishesAllChainTransactionOutcomesToTheWorkflow() {
        ZeebeClient client = mock(ZeebeClient.class, RETURNS_DEEP_STUBS);
        PlatformEventConsumer consumer = new PlatformEventConsumer(client);
        List<String> topics = List.of(
            PlatformTopics.CHAIN_TX_SUBMITTED,
            PlatformTopics.CHAIN_TX_CONFIRMED,
            PlatformTopics.CHAIN_TX_FAILED,
            PlatformTopics.CHAIN_TX_REORGED
        );
        List<Map<String, Object>> expectedVariables = List.of(
            Map.of("instructionId", "transfer-1", "transactionHash", "0xabc",
                "transactionOutcome", "PENDING", "finalized", false),
            Map.of("instructionId", "transfer-1", "transactionHash", "0xabc",
                "transactionOutcome", "CONFIRMED", "finalized", true),
            Map.of("instructionId", "transfer-1", "transactionHash", "0xabc",
                "transactionOutcome", "REVERTED", "finalized", true),
            Map.of("instructionId", "transfer-1", "transactionHash", "0xabc",
                "transactionOutcome", "UNKNOWN", "finalized", false)
        );
        List<ConsumerRecord<String, PlatformEvent>> records = new ArrayList<>();
        for (int index = 0; index < topics.size(); index++) {
            records.add(new ConsumerRecord<>(
                topics.get(index),
                0,
                index,
                "transfer-1",
                new PlatformEvent(
                    "event-" + index,
                    "TransactionUpdate",
                    "transfer-1",
                    Instant.EPOCH,
                    31337L,
                    "0xabc",
                    (long) index,
                    1,
                    Map.of()
                )
            ));
        }

        records.forEach(consumer::onTransaction);

        for (int index = 0; index < expectedVariables.size(); index++) {
            var command = client.newPublishMessageCommand()
                .messageName("TransferTransactionResult")
                .correlationKey("transfer-1")
                .messageId("chain-transaction-result:"
                    + expectedVariables.get(index).get("transactionOutcome") + ":event-" + index);
            var ttlCommand = command.timeToLive(Duration.ofMinutes(15));
            ArgumentCaptor<Map<String, Object>> variables = mapCaptor();
            verify(ttlCommand).variables(variables.capture());
            assertThat(variables.getValue()).isEqualTo(expectedVariables.get(index));
        }
    }

    @Test
    void rejectsChainEventsWithoutCorrelationOrTransactionIdentity() {
        ZeebeClient client = mock(ZeebeClient.class, RETURNS_DEEP_STUBS);
        PlatformEventConsumer consumer = new PlatformEventConsumer(client);
        PlatformEvent event = new PlatformEvent(
            "event-1", "ChainTxConfirmed", " ", Instant.EPOCH, 31337L, "0xabc", 1L, 1, Map.of()
        );
        ConsumerRecord<String, PlatformEvent> record =
            new ConsumerRecord<>(PlatformTopics.CHAIN_TX_CONFIRMED, 0, 0L, "transfer-1", event);

        assertThrows(IllegalArgumentException.class, () -> consumer.onTransaction(record));
        verify(client, never()).newPublishMessageCommand();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
    }
}
