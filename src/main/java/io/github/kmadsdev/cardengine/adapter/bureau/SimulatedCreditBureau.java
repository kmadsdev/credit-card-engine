package io.github.kmadsdev.cardengine.adapter.bureau;

import io.github.kmadsdev.cardengine.application.port.CreditBureau;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SIMULATION. No credit bureau is called. The score is a pure function of the CPF:
 * SHA-256 of the digits, mapped to 250..949. The same CPF always gets the same score,
 * so tests and demos repeat. This is not a credit model.
 */
@Component
public class SimulatedCreditBureau implements CreditBureau {

    static final int MIN = 250;
    static final int SPAN = 700;

    @Override
    public int scoreFor(String cpf) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(cpf.getBytes(StandardCharsets.UTF_8));
            int value = ((hash[0] & 0xff) << 8) | (hash[1] & 0xff);
            return MIN + value % SPAN;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always present in the JDK", e);
        }
    }
}
