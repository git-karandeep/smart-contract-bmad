package ai.generalcontracts.startup.messaging;

import java.time.Instant;
import java.util.Map;

public record PlatformEvent(
    String eventId,
    String eventType,
    String businessId,
    Instant occurredAt,
    Long chainId,
    String txHash,
    Long blockNumber,
    int schemaVersion,
    Map<String, Object> payload
) {
}
