package io.github.kmadsdev.cardengine.domain.policy;

import io.github.kmadsdev.cardengine.domain.CardVariant;

import java.math.BigDecimal;

/**
 * What the upgrade policy knows about an existing account.
 *
 * @param currentVariant        variant of the account today
 * @param currentLimit          credit limit today (0.00 for debit)
 * @param monthsActive          full months since the account was opened
 * @param statementCount        number of statements in the payment history
 * @param onTimeRatio           share of statements paid on time, 0..1 (1 when there are none)
 * @param utilization           latest statement balance / current limit, 0..n (0 for debit)
 * @param declaredMonthlyIncome latest income the client declared, in BRL
 * @param score                 simulated bureau score, 0..1000
 */
public record UpgradeFacts(
        CardVariant currentVariant,
        BigDecimal currentLimit,
        long monthsActive,
        int statementCount,
        BigDecimal onTimeRatio,
        BigDecimal utilization,
        BigDecimal declaredMonthlyIncome,
        int score) {
}
