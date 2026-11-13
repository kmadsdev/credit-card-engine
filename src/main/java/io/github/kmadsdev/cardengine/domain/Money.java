package io.github.kmadsdev.cardengine.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Amounts in BRL. Every amount in the engine is a {@link BigDecimal} with scale 2
 * (whole centavos). A value with more precision is refused, not rounded, so a bad
 * input cannot move a limit across a variant boundary by accident.
 */
public final class Money {

    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private Money() {
    }

    public static BigDecimal of(String value) {
        return of(new BigDecimal(value));
    }

    /** Returns the value with scale 2. Throws when it is negative or has fractions of a centavo. */
    public static BigDecimal of(BigDecimal value) {
        if (value == null) {
            throw new DomainException("invalid-amount", "Amount is required");
        }
        if (value.signum() < 0) {
            throw new DomainException("invalid-amount", "Amount must not be negative");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new DomainException("invalid-amount", "Amount must have at most 2 decimal places");
        }
    }

    /** Rounds down to a whole multiple of {@code step} (for example R$ 10.00). */
    public static BigDecimal floorTo(BigDecimal value, BigDecimal step) {
        BigDecimal steps = value.divide(step, 0, RoundingMode.DOWN);
        return steps.multiply(step).setScale(2, RoundingMode.UNNECESSARY);
    }
}
