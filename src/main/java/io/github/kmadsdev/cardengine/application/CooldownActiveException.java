package io.github.kmadsdev.cardengine.application;

import java.time.Instant;

/** An upgrade was requested before the cooldown since the last request ended. */
public class CooldownActiveException extends RuntimeException {

    private final Instant retryAt;

    public CooldownActiveException(Instant retryAt) {
        super("A new upgrade request is allowed from " + retryAt);
        this.retryAt = retryAt;
    }

    public Instant retryAt() {
        return retryAt;
    }
}
