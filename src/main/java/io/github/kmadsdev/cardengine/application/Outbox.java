package io.github.kmadsdev.cardengine.application;

import io.github.kmadsdev.cardengine.persistence.OutboxEvent;
import io.github.kmadsdev.cardengine.persistence.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Writes an event to the outbox table. It must run inside the transaction of the
 * state change ({@link Propagation#MANDATORY}), so an event can never exist without
 * its change, or a change without its event.
 */
@Component
class Outbox {

    private final OutboxEventRepository events;
    private final Clock clock;

    Outbox(OutboxEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void append(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        events.save(new OutboxEvent(aggregateType, aggregateId, eventType, payload, clock.instant()));
    }
}
