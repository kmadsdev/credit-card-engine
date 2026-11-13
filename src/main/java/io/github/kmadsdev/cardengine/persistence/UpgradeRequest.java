package io.github.kmadsdev.cardengine.persistence;

import io.github.kmadsdev.cardengine.domain.CardVariant;
import io.github.kmadsdev.cardengine.domain.UpgradeStatus;
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

@Entity
@Table(name = "upgrade_request")
public class UpgradeRequest {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UpgradeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_variant", nullable = false, updatable = false)
    private CardVariant fromVariant;

    @Column(name = "from_limit", nullable = false, updatable = false)
    private BigDecimal fromLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_variant")
    private CardVariant toVariant;

    @Column(name = "to_limit")
    private BigDecimal toLimit;

    @Column(name = "declared_monthly_income", nullable = false, updatable = false)
    private BigDecimal declaredMonthlyIncome;

    @Column(name = "bureau_score", nullable = false, updatable = false)
    private int bureauScore;

    @Column(name = "decision_id", nullable = false)
    private UUID decisionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected UpgradeRequest() {
    }

    public UpgradeRequest(UUID id, String idempotencyKey, String requestHash, CardAccount account,
                          BigDecimal declaredMonthlyIncome, int bureauScore, UUID decisionId, Instant now) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.accountId = account.getId();
        this.status = UpgradeStatus.REQUESTED;
        this.fromVariant = account.getVariant();
        this.fromLimit = account.getCreditLimit();
        this.declaredMonthlyIncome = declaredMonthlyIncome;
        this.bureauScore = bureauScore;
        this.decisionId = decisionId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void approve(CardVariant target, BigDecimal newLimit, Instant now) {
        move(UpgradeStatus.APPROVED, now);
        this.toVariant = target;
        this.toLimit = newLimit;
    }

    public void markApplied(Instant now) {
        move(UpgradeStatus.APPLIED, now);
    }

    public void keep(Instant now) {
        move(UpgradeStatus.KEPT, now);
    }

    public void reject(Instant now) {
        move(UpgradeStatus.REJECTED, now);
    }

    private void move(UpgradeStatus next, Instant now) {
        this.status = status.moveTo(next);
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UpgradeStatus getStatus() {
        return status;
    }

    public CardVariant getFromVariant() {
        return fromVariant;
    }

    public BigDecimal getFromLimit() {
        return fromLimit;
    }

    public CardVariant getToVariant() {
        return toVariant;
    }

    public BigDecimal getToLimit() {
        return toLimit;
    }

    public BigDecimal getDeclaredMonthlyIncome() {
        return declaredMonthlyIncome;
    }

    public int getBureauScore() {
        return bureauScore;
    }

    public UUID getDecisionId() {
        return decisionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "UpgradeRequest[id=" + id + ", status=" + status + "]";
    }
}
