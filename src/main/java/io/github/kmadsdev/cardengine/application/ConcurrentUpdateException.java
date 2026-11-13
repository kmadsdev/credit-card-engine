package io.github.kmadsdev.cardengine.application;

/** Another request changed the same row first (optimistic lock or unique key). Retry may succeed. */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
