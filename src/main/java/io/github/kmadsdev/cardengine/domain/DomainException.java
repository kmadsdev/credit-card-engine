package io.github.kmadsdev.cardengine.domain;

/**
 * A business rule was broken. The {@code code} is stable and goes into the API error
 * (RFC 7807 "type"), so a client can branch on it without parsing the message.
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
