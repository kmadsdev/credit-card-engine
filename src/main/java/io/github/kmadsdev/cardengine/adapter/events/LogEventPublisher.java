package io.github.kmadsdev.cardengine.adapter.events;

import io.github.kmadsdev.cardengine.application.port.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Writes each event as one structured log line. It stands where a message broker
 * adapter would go (ADR 0005). Payloads carry ids, variants and amounts, never a CPF
 * or a card number.
 */
@Component
public class LogEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger("events");

    @Override
    public void publish(Event event) {
        log.info("event {} {}", kv("eventType", event.type()), kv("eventId", event.id()),
                kv("aggregateType", event.aggregateType()), kv("aggregateId", event.aggregateId()),
                kv("occurredAt", event.occurredAt()), kv("payload", event.payload()));
    }
}
