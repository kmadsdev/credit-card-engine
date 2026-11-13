package io.github.kmadsdev.cardengine.adapter.vault;

import io.github.kmadsdev.cardengine.application.port.CardVault;
import io.github.kmadsdev.cardengine.domain.Luhn;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * SIMULATION of a card vault. It creates a Luhn-valid test number (prefix
 * {@link Luhn#TEST_PREFIX}), returns a random token and the last 4 digits, and then
 * drops the number. Nothing is stored, so a token here cannot be turned back into a
 * number. A real vault (a PCI DSS scoped service) would keep the number and
 * detokenize it for authorized callers only. See ADR 0004.
 */
@Component
public class SimulatedCardVault implements CardVault {

    private final SecureRandom random = new SecureRandom();

    @Override
    public VaultedCard issueNumber() {
        String pan = Luhn.generateTestPan(random);
        byte[] tokenBytes = new byte[16];
        random.nextBytes(tokenBytes);
        return new VaultedCard("tok_" + HexFormat.of().formatHex(tokenBytes), pan.substring(pan.length() - 4));
    }
}
