package io.github.kmadsdev.cardengine.application.port;

/**
 * Port to the card vault. The vault creates the card number (PAN), keeps it, and
 * gives back a token and the last 4 digits. The full number never enters the rest of
 * the application, so it cannot reach the database or a log line (ADR 0004).
 */
public interface CardVault {

    VaultedCard issueNumber();

    record VaultedCard(String token, String last4) {
        public VaultedCard {
            if (last4 == null || !last4.matches("\\d{4}")) {
                throw new IllegalArgumentException("last4 must be exactly 4 digits");
            }
        }
    }
}
