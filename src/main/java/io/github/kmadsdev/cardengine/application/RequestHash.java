package io.github.kmadsdev.cardengine.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SHA-256 of the request fields. Stored next to the Idempotency-Key, so a replay with
 * the same key but a different body is refused instead of returning the wrong result.
 */
final class RequestHash {

    private RequestHash() {
    }

    static String of(Object... fields) {
        String canonical = Stream.of(fields).map(f -> Objects.toString(f, "")).collect(Collectors.joining("\u001f"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always present in the JDK", e);
        }
    }
}
