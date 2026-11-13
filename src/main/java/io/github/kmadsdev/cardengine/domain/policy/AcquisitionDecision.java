package io.github.kmadsdev.cardengine.domain.policy;

import io.github.kmadsdev.cardengine.domain.CardVariant;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of the acquisition policy. When {@code approved} is false, {@code variant}
 * and {@code limit} are null. {@code reasons} always says why.
 */
public record AcquisitionDecision(
        boolean approved,
        CardVariant variant,
        BigDecimal limit,
        List<Reason> reasons,
        String policyVersion) {

    public AcquisitionDecision {
        reasons = List.copyOf(reasons);
    }
}
