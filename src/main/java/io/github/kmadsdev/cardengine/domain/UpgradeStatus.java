package io.github.kmadsdev.cardengine.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * States of an upgrade request (existing client).
 *
 * <pre>
 * REQUESTED -> APPROVED -> APPLIED
 *     |-> KEPT
 *     +-> REJECTED
 * </pre>
 *
 * APPROVED means the policy said yes. APPLIED means the account row was changed.
 * The two are separate so a lost optimistic-lock race can never look applied.
 */
public enum UpgradeStatus {
    REQUESTED,
    APPROVED,
    APPLIED,
    KEPT,
    REJECTED;

    public Set<UpgradeStatus> allowedNext() {
        return switch (this) {
            case REQUESTED -> EnumSet.of(APPROVED, KEPT, REJECTED);
            case APPROVED -> EnumSet.of(APPLIED);
            case APPLIED, KEPT, REJECTED -> EnumSet.noneOf(UpgradeStatus.class);
        };
    }

    public boolean canMoveTo(UpgradeStatus next) {
        return allowedNext().contains(next);
    }

    public UpgradeStatus moveTo(UpgradeStatus next) {
        if (!canMoveTo(next)) {
            throw new IllegalStateTransitionException(this, next);
        }
        return next;
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
