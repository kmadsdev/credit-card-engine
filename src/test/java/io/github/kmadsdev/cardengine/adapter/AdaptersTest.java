package io.github.kmadsdev.cardengine.adapter;

import io.github.kmadsdev.cardengine.adapter.bureau.SimulatedCreditBureau;
import io.github.kmadsdev.cardengine.adapter.events.LogEventPublisher;
import io.github.kmadsdev.cardengine.adapter.vault.SimulatedCardVault;
import io.github.kmadsdev.cardengine.application.port.CardVault;
import io.github.kmadsdev.cardengine.application.port.EventPublisher;
import io.github.kmadsdev.cardengine.testsupport.TestCpfs;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptersTest {

    @RepeatedTest(50)
    void simulatedBureauIsDeterministicAndInRange() {
        var bureau = new SimulatedCreditBureau();
        String cpf = TestCpfs.random();
        int score = bureau.scoreFor(cpf);
        assertThat(score).isBetween(250, 949).isEqualTo(bureau.scoreFor(cpf));
    }

    @Test
    void simulatedVaultReturnsRandomTokenAndFourDigits() {
        var vault = new SimulatedCardVault();
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            CardVault.VaultedCard card = vault.issueNumber();
            assertThat(card.token()).matches("tok_[0-9a-f]{32}");
            assertThat(card.last4()).matches("\\d{4}");
            tokens.add(card.token());
        }
        assertThat(tokens).hasSize(200);
    }

    @Test
    void vaultedCardRefusesAnythingButFourDigits() {
        assertThatThrownBy(() -> new CardVault.VaultedCard("tok_x", "1234567812345678"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void logPublisherAcceptsAnEvent() {
        var event = new EventPublisher.Event(UUID.randomUUID(), "CardIssued", "card", UUID.randomUUID(),
                Instant.now(), Map.of("last4", "1234"));
        assertThatCode(() -> new LogEventPublisher().publish(event)).doesNotThrowAnyException();
    }
}
