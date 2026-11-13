package io.github.kmadsdev.cardengine.testsupport;

import io.github.kmadsdev.cardengine.application.port.EventPublisher;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Keeps every published event in memory; can be told to fail. */
public class RecordingEventPublisher implements EventPublisher {

    private final List<Event> published = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    public void failing(boolean failing) {
        this.failing = failing;
    }

    public List<Event> published() {
        return published;
    }

    @Override
    public void publish(Event event) {
        if (failing) {
            throw new IllegalStateException("broker down (simulated)");
        }
        published.add(event);
    }
}
