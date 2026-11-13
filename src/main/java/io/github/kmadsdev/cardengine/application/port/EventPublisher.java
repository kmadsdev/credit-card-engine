package io.github.kmadsdev.cardengine.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Port the outbox publisher sends events through. The adapter in this repository
 * writes them to the log. A message broker adapter would implement the same
 * interface (ADR 0005); none is included.
 */
public interface EventPublisher {

    /** Throws when the event could not be delivered; the outbox row stays pending. */
    void publish(Event event);

    record Event(UUID id, String type, String aggregateType, UUID aggregateId,
                 Instant occurredAt, Map<String, Object> payload) {
    }
}
