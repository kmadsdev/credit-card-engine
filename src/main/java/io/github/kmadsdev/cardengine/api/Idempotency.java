package io.github.kmadsdev.cardengine.api;

import io.github.kmadsdev.cardengine.application.Views.Created;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/** Shared header name and response rule for the create endpoints. */
final class Idempotency {

    static final String HEADER = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";
    static final String KEY_PATTERN = "[A-Za-z0-9_-]{8,80}";

    private Idempotency() {
    }

    /** 201 with Location the first time; 200 with {@code Idempotent-Replayed: true} on a replay. */
    static <T> ResponseEntity<T> respond(Created<T> created, URI location) {
        if (created.replayed()) {
            return ResponseEntity.status(HttpStatus.OK).header(REPLAYED_HEADER, "true")
                    .location(location).body(created.body());
        }
        return ResponseEntity.created(location).body(created.body());
    }
}
