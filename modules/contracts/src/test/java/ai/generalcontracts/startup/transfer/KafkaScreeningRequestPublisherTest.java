package ai.generalcontracts.startup.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.generalcontracts.startup.messaging.PlatformTopics;
import ai.generalcontracts.startup.workflow.ScreeningRequestMessage;
import ai.generalcontracts.startup.workflow.ScreeningType;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaScreeningRequestPublisherTest {
    @Test
    @SuppressWarnings("unchecked")
    void publishesMinimalRequestWithStableIdAsKafkaKeyAndWaitsForBrokerAck() {
        KafkaTemplate<String, ScreeningRequestMessage> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        ScreeningRequestMessage request = new ScreeningRequestMessage(
            "transfer-1",
            "transfer-1:KYC",
            "transfer-1:KYC",
            "configured-provider",
            ScreeningType.KYC,
            "approved-version",
            Instant.parse("2026-10-11T20:00:00Z")
        );

        new KafkaScreeningRequestPublisher(kafka).publish(request);

        var captor = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, ScreeningRequestMessage> record =
            (ProducerRecord<String, ScreeningRequestMessage>) captor.getValue();
        assertEquals(PlatformTopics.SCREENING_REQUESTS, record.topic());
        assertEquals(request.screeningRequestId(), record.key());
        assertEquals(request, record.value());
        assertNotNull(record.headers().lastHeader("X-Screening-Request-Id"));
    }
}
