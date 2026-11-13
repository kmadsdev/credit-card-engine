package io.github.kmadsdev.cardengine.persistence;

import io.github.kmadsdev.cardengine.domain.ApplicationStatus;
import io.github.kmadsdev.cardengine.domain.CardVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A card application. Every status change goes through
 * {@link ApplicationStatus#moveTo}, so an illegal move throws before it reaches the
 * database.
 */
@Entity
@Table(name = "card_application")
public class CardApplication {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(nullable = false, updatable = false)
    private String cpf;

    @Column(name = "full_name", nullable = false, updatable = false)
    private String fullName;

    @Column(name = "birth_date", nullable = false, updatable = false)
    private LocalDate birthDate;

    @Column(name = "declared_monthly_income", nullable = false, updatable = false)
    private BigDecimal declaredMonthlyIncome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "bureau_score")
    private Integer bureauScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "approved_variant")
    private CardVariant approvedVariant;

    @Column(name = "approved_limit")
    private BigDecimal approvedLimit;

    @Column(name = "decision_id")
    private UUID decisionId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "card_id")
    private UUID cardId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected CardApplication() {
    }

    public CardApplication(String idempotencyKey, String requestHash, String cpf, String fullName,
                           LocalDate birthDate, BigDecimal declaredMonthlyIncome, Instant now) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.cpf = cpf;
        this.fullName = fullName;
        this.birthDate = birthDate;
        this.declaredMonthlyIncome = declaredMonthlyIncome;
        this.status = ApplicationStatus.SUBMITTED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markValidated(Instant now) {
        move(ApplicationStatus.VALIDATED, now);
    }

    public void reject(UUID decisionId, Integer score, Instant now) {
        move(ApplicationStatus.REJECTED, now);
        this.decisionId = decisionId;
        this.bureauScore = score;
    }

    public void approve(CardVariant variant, BigDecimal limit, int score, UUID decisionId, Instant now) {
        move(ApplicationStatus.APPROVED, now);
        this.approvedVariant = variant;
        this.approvedLimit = limit;
        this.bureauScore = score;
        this.decisionId = decisionId;
    }

    public void accountOpened(UUID customerId, UUID accountId, Instant now) {
        move(ApplicationStatus.ACCOUNT_OPENED, now);
        this.customerId = customerId;
        this.accountId = accountId;
    }

    public void cardIssued(UUID cardId, Instant now) {
        move(ApplicationStatus.CARD_ISSUED, now);
        this.cardId = cardId;
    }

    private void move(ApplicationStatus next, Instant now) {
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

    public String getCpf() {
        return cpf;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public BigDecimal getDeclaredMonthlyIncome() {
        return declaredMonthlyIncome;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public Integer getBureauScore() {
        return bureauScore;
    }

    public CardVariant getApprovedVariant() {
        return approvedVariant;
    }

    public BigDecimal getApprovedLimit() {
        return approvedLimit;
    }

    public UUID getDecisionId() {
        return decisionId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getCardId() {
        return cardId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "CardApplication[id=" + id + ", status=" + status + "]";
    }
}
