package io.github.kmadsdev.cardengine.application.port;

/**
 * Port to a credit bureau. The only adapter in this repository is a deterministic
 * simulation ({@code SimulatedCreditBureau}); no real bureau is called. ADR 0003 says
 * where a real one plugs in.
 */
public interface CreditBureau {

    /** Score from 0 to 1000 for the person with this CPF (11 digits). */
    int scoreFor(String cpf);
}
