package io.github.kmadsdev.cardengine.application;

import io.github.kmadsdev.cardengine.application.port.EventPublisher;
import io.github.kmadsdev.cardengine.persistence.OutboxEvent;
import io.github.kmadsdev.cardengine.persistence.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

/**
 * Sends pending outbox rows through the {@link EventPublisher} port.
 *
 * <p>Delivery is at least once: a crash after {@code publish} and before the commit
 * sends the event again on the next run. Consumers deduplicate by event id.
 * A failed event stays pending and is tried again; it does not block the others.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository events;
    private final EventPublisher publisher;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MeterRegistry meters;
    private final int batchSize;

    public OutboxPublisher(OutboxEventRepository events, EventPublisher publisher, TransactionTemplate tx,
                           Clock clock, MeterRegistry meters, @Value("${app.outbox.batch-size:50}") int batchSize) {
        this.events = events;
        this.publisher = publisher;
        this.tx = tx;
        this.clock = clock;
        this.meters = meters;
        this.batchSize = batchSize;
        Gauge.builder("cards.outbox.pending", events, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Outbox events not published yet")
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${app.outbox.publish-interval:PT2S}")
    public void scheduledRun() {
        publishPending();
    }

    /** Publishes one batch. Returns how many events were published. */
    public int publishPending() {
        Integer published = tx.execute(status -> {
            List<OutboxEvent> batch = events.lockPendingBatch(batchSize);
            int ok = 0;
            for (OutboxEvent event : batch) {
                try {
                    publisher.publish(new EventPublisher.Event(event.getId(), event.getEventType(),
                            event.getAggregateType(), event.getAggregateId(), event.getCreatedAt(), event.getPayload()));
                    event.markPublished(clock.instant());
                    ok++;
                } catch (RuntimeException e) {
                    event.markFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
                    meters.counter("cards.outbox.failures", "type", event.getEventType()).increment();
                    log.warn("outbox publish failed id={} type={} attempts={}", event.getId(),
                            event.getEventType(), event.getAttempts());
                }
            }
            return ok;
        });
        return published == null ? 0 : published;
    }
}
