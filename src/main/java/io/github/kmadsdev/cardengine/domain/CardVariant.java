package io.github.kmadsdev.cardengine.domain;

import java.util.Optional;

/**
 * Card variants, in upgrade order. The limit band of each variant is data in
 * {@link io.github.kmadsdev.cardengine.domain.policy.CreditPolicy#VARIANT_BANDS},
 * not code here, so all numbers live in one table.
 */
public enum CardVariant {
    DEBIT,
    CREDIT_BASE,
    CREDIT_PLATINUM,
    CREDIT_BLACK;

    /** The variant one step up, or empty for the top variant. */
    public Optional<CardVariant> next() {
        CardVariant[] all = values();
        return ordinal() + 1 < all.length ? Optional.of(all[ordinal() + 1]) : Optional.empty();
    }

    public boolean isAbove(CardVariant other) {
        return ordinal() > other.ordinal();
    }

    public boolean isCredit() {
        return this != DEBIT;
    }
}
