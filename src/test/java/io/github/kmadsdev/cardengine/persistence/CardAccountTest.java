package io.github.kmadsdev.cardengine.persistence;

import io.github.kmadsdev.cardengine.domain.ApplicationStatus;
import io.github.kmadsdev.cardengine.domain.CardVariant;
import io.github.kmadsdev.cardengine.domain.DomainException;
import io.github.kmadsdev.cardengine.domain.IllegalStateTransitionException;
import io.github.kmadsdev.cardengine.domain.Money;
import io.github.kmadsdev.cardengine.domain.UpgradeStatus;
import io.github.kmadsdev.cardengine.testsupport.TestCpfs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Entity rules that need no database. */
class CardAccountTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private static CardAccount platinum() {
        return new CardAccount(UUID.randomUUID(), CardVariant.CREDIT_PLATINUM, Money.of("5000.00"), NOW);
    }

    @ParameterizedTest
    @EnumSource(value = CardVariant.class, names = {"DEBIT", "CREDIT_BASE", "CREDIT_PLATINUM"})
    void noDowngradeAndNoSameVariant(CardVariant target) {
        CardAccount account = platinum();
        BigDecimal limit = target == CardVariant.DEBIT ? Money.ZERO : Money.of("2000.00");
        assertThatThrownBy(() -> account.upgradeTo(target, target == CardVariant.CREDIT_PLATINUM
                ? Money.of("7000.00") : limit, NOW))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("downgrade-not-allowed");
        assertThat(account.getVariant()).isEqualTo(CardVariant.CREDIT_PLATINUM);
    }

    @Test
    void limitMustMatchTheVariantBand() {
        assertThatThrownBy(() -> new CardAccount(UUID.randomUUID(), CardVariant.CREDIT_BASE, Money.of("2000.01"), NOW))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("limit-out-of-band");
        assertThatThrownBy(() -> platinum().upgradeTo(CardVariant.CREDIT_BLACK, Money.of("8000.00"), NOW))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("limit-out-of-band");
    }

    @Test
    void upgradeToHigherVariant() {
        CardAccount account = platinum();
        account.upgradeTo(CardVariant.CREDIT_BLACK, Money.of("8000.01"), NOW.plusSeconds(1));
        assertThat(account.getVariant()).isEqualTo(CardVariant.CREDIT_BLACK);
        assertThat(account.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void applicationRefusesIllegalMoves() {
        var app = new CardApplication("k-12345678", "h", TestCpfs.random(), "n", LocalDate.of(1990, 1, 1),
                Money.of("1.00"), NOW);
        assertThatThrownBy(() -> app.approve(CardVariant.DEBIT, Money.ZERO, 400, UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalStateTransitionException.class);
        app.reject(UUID.randomUUID(), null, NOW);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThatThrownBy(() -> app.markValidated(NOW)).isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void upgradeRequestCannotBeAppliedTwice() {
        var request = new UpgradeRequest(UUID.randomUUID(), "k", "h", platinum(), Money.of("1.00"), 700,
                UUID.randomUUID(), NOW);
        assertThatThrownBy(() -> request.markApplied(NOW)).isInstanceOf(IllegalStateTransitionException.class);
        request.approve(CardVariant.CREDIT_BLACK, Money.of("9000.00"), NOW);
        request.markApplied(NOW);
        assertThat(request.getStatus()).isEqualTo(UpgradeStatus.APPLIED);
        assertThatThrownBy(() -> request.markApplied(NOW)).isInstanceOf(IllegalStateTransitionException.class);
    }
}
