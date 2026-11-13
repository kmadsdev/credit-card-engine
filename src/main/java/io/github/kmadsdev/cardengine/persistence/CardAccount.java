package io.github.kmadsdev.cardengine.persistence;

import io.github.kmadsdev.cardengine.domain.CardVariant;
import io.github.kmadsdev.cardengine.domain.DomainException;
import io.github.kmadsdev.cardengine.domain.policy.CreditPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A card account: one per customer. {@link #version} is the optimistic lock that
 * makes two concurrent upgrades of the same account fail instead of both applying.
 */
@Entity
@Table(name = "card_account")
public class CardAccount {

    public enum Status { ACTIVE }

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardVariant variant;

    @Column(name = "credit_limit", nullable = false)
    private BigDecimal creditLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_upgrade_request_at")
    private Instant lastUpgradeRequestAt;

    @Version
    private Long version;

    protected CardAccount() {
    }

    public CardAccount(UUID customerId, CardVariant variant, BigDecimal creditLimit, Instant openedAt) {
        requireLimitMatchesVariant(variant, creditLimit);
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.variant = variant;
        this.creditLimit = creditLimit;
        this.status = Status.ACTIVE;
        this.openedAt = openedAt;
        this.updatedAt = openedAt;
    }

    /**
     * Moves the account to a higher variant. There is no method to move it down:
     * a downgrade is a different business process that this engine does not offer.
     */
    public void upgradeTo(CardVariant target, BigDecimal newLimit, Instant now) {
        if (!target.isAbove(variant)) {
            throw new DomainException("downgrade-not-allowed",
                    "Account is " + variant + "; " + target + " is not an upgrade");
        }
        requireLimitMatchesVariant(target, newLimit);
        this.variant = target;
        this.creditLimit = newLimit;
        this.updatedAt = now;
    }

    /**
     * Stores the time of an upgrade request, for the cooldown. Every request calls it,
     * also one that changes nothing else, so every request writes this row and goes
     * through the {@link #version} check.
     */
    public void recordUpgradeRequest(Instant now) {
        this.lastUpgradeRequestAt = now;
    }

    private static void requireLimitMatchesVariant(CardVariant variant, BigDecimal limit) {
        if (CreditPolicy.variantFor(limit) != variant) {
            throw new DomainException("limit-out-of-band",
                    "Limit " + limit.toPlainString() + " is outside the " + variant + " band");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public CardVariant getVariant() {
        return variant;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastUpgradeRequestAt() {
        return lastUpgradeRequestAt;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "CardAccount[id=" + id + ", variant=" + variant + "]";
    }
}
