package io.github.kmadsdev.cardengine.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One monthly statement of an account, simulated. In a real system this would come
 * from the billing platform; here tests and the demo profile write it.
 */
@Entity
@Table(name = "payment_history")
public class PaymentHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "statement_balance", nullable = false)
    private BigDecimal statementBalance;

    @Column(name = "paid_on_time", nullable = false)
    private boolean paidOnTime;

    protected PaymentHistoryEntry() {
    }

    public PaymentHistoryEntry(UUID accountId, LocalDate referenceMonth, BigDecimal statementBalance, boolean paidOnTime) {
        this.accountId = accountId;
        this.referenceMonth = referenceMonth.withDayOfMonth(1);
        this.statementBalance = statementBalance;
        this.paidOnTime = paidOnTime;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LocalDate getReferenceMonth() {
        return referenceMonth;
    }

    public BigDecimal getStatementBalance() {
        return statementBalance;
    }

    public boolean isPaidOnTime() {
        return paidOnTime;
    }
}
