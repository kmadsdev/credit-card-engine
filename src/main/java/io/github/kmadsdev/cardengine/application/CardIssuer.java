package io.github.kmadsdev.cardengine.application;

import io.github.kmadsdev.cardengine.application.port.CardVault;
import io.github.kmadsdev.cardengine.domain.CardStatus;
import io.github.kmadsdev.cardengine.persistence.Card;
import io.github.kmadsdev.cardengine.persistence.CardAccount;
import io.github.kmadsdev.cardengine.persistence.CardRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;

/** Issues a card for an account: number from the vault, token and last 4 to the database. */
@Component
class CardIssuer {

    static final int VALIDITY_YEARS = 5;

    private final CardVault vault;
    private final CardRepository cards;
    private final Outbox outbox;
    private final Clock clock;

    CardIssuer(CardVault vault, CardRepository cards, Outbox outbox, Clock clock) {
        this.vault = vault;
        this.cards = cards;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Issues a new card and marks every active card of the account as replaced. */
    @Transactional(propagation = Propagation.MANDATORY)
    Card issueFor(CardAccount account) {
        Instant now = clock.instant();
        cards.findByAccountIdAndStatus(account.getId(), CardStatus.ACTIVE).forEach(Card::replace);

        CardVault.VaultedCard vaulted = vault.issueNumber();
        YearMonth expiry = YearMonth.from(now.atZone(ZoneOffset.UTC)).plusYears(VALIDITY_YEARS);
        Card card = cards.save(new Card(account.getId(), account.getVariant(), vaulted.token(), vaulted.last4(),
                expiry.getMonthValue(), expiry.getYear(), now));

        outbox.append("card", card.getId(), "CardIssued", Map.of(
                "cardId", card.getId().toString(),
                "accountId", account.getId().toString(),
                "variant", card.getVariant().name(),
                "last4", card.getLast4()));
        return card;
    }
}
