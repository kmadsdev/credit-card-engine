package io.github.kmadsdev.cardengine.application;

import io.github.kmadsdev.cardengine.domain.ApplicationStatus;
import io.github.kmadsdev.cardengine.domain.CardStatus;
import io.github.kmadsdev.cardengine.domain.CardVariant;
import io.github.kmadsdev.cardengine.domain.Cpf;
import io.github.kmadsdev.cardengine.domain.UpgradeStatus;
import io.github.kmadsdev.cardengine.domain.policy.Reason;
import io.github.kmadsdev.cardengine.persistence.Card;
import io.github.kmadsdev.cardengine.persistence.CardAccount;
import io.github.kmadsdev.cardengine.persistence.CardApplication;
import io.github.kmadsdev.cardengine.persistence.DecisionRecord;
import io.github.kmadsdev.cardengine.persistence.UpgradeRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read models returned by the use cases. They carry no full CPF and no card number:
 * the CPF is masked and a card shows only its last 4 digits.
 */
public final class Views {

    private Views() {
    }

    public record DecisionView(UUID id, String outcome, String policyVersion, List<Reason> reasons, Instant decidedAt) {
        static DecisionView of(DecisionRecord d) {
            return d == null ? null
                    : new DecisionView(d.getId(), d.getOutcome(), d.getPolicyVersion(), d.getReasons(), d.getCreatedAt());
        }
    }

    public record ApplicationView(
            UUID id,
            ApplicationStatus status,
            String cpfMasked,
            CardVariant variant,
            BigDecimal creditLimit,
            UUID customerId,
            UUID accountId,
            UUID cardId,
            DecisionView decision,
            Instant createdAt,
            Instant updatedAt) {

        static ApplicationView of(CardApplication a, DecisionRecord decision) {
            return new ApplicationView(a.getId(), a.getStatus(), Cpf.mask(a.getCpf()), a.getApprovedVariant(),
                    a.getApprovedLimit(), a.getCustomerId(), a.getAccountId(), a.getCardId(),
                    DecisionView.of(decision), a.getCreatedAt(), a.getUpdatedAt());
        }
    }

    public record AccountView(UUID id, UUID customerId, CardVariant variant, BigDecimal creditLimit,
                              String status, Instant openedAt, Instant updatedAt, long version) {
        static AccountView of(CardAccount a) {
            return new AccountView(a.getId(), a.getCustomerId(), a.getVariant(), a.getCreditLimit(),
                    a.getStatus().name(), a.getOpenedAt(), a.getUpdatedAt(), a.getVersion());
        }
    }

    public record CardView(UUID id, UUID accountId, CardVariant variant, String maskedNumber,
                           String expiry, CardStatus status, Instant issuedAt) {
        static CardView of(Card c) {
            return new CardView(c.getId(), c.getAccountId(), c.getVariant(), "**** **** **** " + c.getLast4(),
                    String.format("%02d/%d", c.getExpiryMonth(), c.getExpiryYear()), c.getStatus(), c.getIssuedAt());
        }
    }

    public record UpgradeRequestView(
            UUID id,
            UUID accountId,
            UpgradeStatus status,
            CardVariant fromVariant,
            BigDecimal fromLimit,
            CardVariant toVariant,
            BigDecimal toLimit,
            DecisionView decision,
            Instant createdAt) {

        static UpgradeRequestView of(UpgradeRequest r, DecisionRecord decision) {
            return new UpgradeRequestView(r.getId(), r.getAccountId(), r.getStatus(), r.getFromVariant(),
                    r.getFromLimit(), r.getToVariant(), r.getToLimit(), DecisionView.of(decision), r.getCreatedAt());
        }
    }

    /** A create result: the resource, plus whether it came from an earlier call with the same key. */
    public record Created<T>(T body, boolean replayed) {
    }
}
