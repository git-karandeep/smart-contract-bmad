package ai.generalcontracts.startup.messaging;

import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.support.SendResult;

public interface PlatformEventPublisher {
    CompletableFuture<SendResult<String, PlatformEvent>> publish(String topic, PlatformEvent event);
}
