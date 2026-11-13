package io.github.kmadsdev.cardengine.application;

/** The same Idempotency-Key came back with a different request body. */
public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException() {
        super("This Idempotency-Key was already used with a different request");
    }
}
