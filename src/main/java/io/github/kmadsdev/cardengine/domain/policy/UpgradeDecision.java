package io.github.kmadsdev.cardengine.domain.policy;

import io.github.kmadsdev.cardengine.domain.CardVariant;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of the upgrade policy. {@code targetVariant} and {@code newLimit} are set
 * only when the outcome is {@link Outcome#APPROVED}.
 */
public record UpgradeDecision(
        Outcome outcome,
        CardVariant targetVariant,
        BigDecimal newLimit,
        List<Reason> reasons,
        String policyVersion) {

    public enum Outcome { APPROVED, KEPT, REJECTED }

    public UpgradeDecision {
        reasons = List.copyOf(reasons);
    }
}
