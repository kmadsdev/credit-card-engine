package io.github.kmadsdev.cardengine.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    private UUID id;

    @Column(nullable = false, length = 11)
    private String cpf;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "declared_monthly_income", nullable = false)
    private BigDecimal declaredMonthlyIncome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Customer() {
    }

    public Customer(String cpf, String fullName, LocalDate birthDate, BigDecimal declaredMonthlyIncome, Instant now) {
        this.id = UUID.randomUUID();
        this.cpf = cpf;
        this.fullName = fullName;
        this.birthDate = birthDate;
        this.declaredMonthlyIncome = declaredMonthlyIncome;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDeclaredIncome(BigDecimal income, Instant now) {
        this.declaredMonthlyIncome = income;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Never prints the CPF or the name. */
    @Override
    public String toString() {
        return "Customer[id=" + id + "]";
    }
}
