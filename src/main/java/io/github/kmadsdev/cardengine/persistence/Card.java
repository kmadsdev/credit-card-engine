package io.github.kmadsdev.cardengine.persistence;

import io.github.kmadsdev.cardengine.domain.CardStatus;
import io.github.kmadsdev.cardengine.domain.CardVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An issued card. It holds a vault token and the last 4 digits only. There is no
 * field for the full card number, so it cannot be saved by mistake.
 */
@Entity
@Table(name = "card")
public class Card {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CardVariant variant;

    @Column(nullable = false, updatable = false)
    private String token;

    @Column(nullable = false, updatable = false)
    private String last4;

    @Column(name = "expiry_month", nullable = false, updatable = false)
    private int expiryMonth;

    @Column(name = "expiry_year", nullable = false, updatable = false)
    private int expiryYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    protected Card() {
    }

    public Card(UUID accountId, CardVariant variant, String token, String last4,
                int expiryMonth, int expiryYear, Instant issuedAt) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.variant = variant;
        this.token = token;
        this.last4 = last4;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.status = CardStatus.ACTIVE;
        this.issuedAt = issuedAt;
    }

    public void replace() {
        this.status = CardStatus.REPLACED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public CardVariant getVariant() {
        return variant;
    }

    public String getToken() {
        return token;
    }

    public String getLast4() {
        return last4;
    }

    public int getExpiryMonth() {
        return expiryMonth;
    }

    public int getExpiryYear() {
        return expiryYear;
    }

    public CardStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    @Override
    public String toString() {
        return "Card[id=" + id + ", last4=" + last4 + "]";
    }
}
