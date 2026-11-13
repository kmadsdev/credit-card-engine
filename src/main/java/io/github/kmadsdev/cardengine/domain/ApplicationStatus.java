package io.github.kmadsdev.cardengine.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * States of a card application (acquisition flow).
 *
 * <pre>
 * SUBMITTED -> VALIDATED -> APPROVED -> ACCOUNT_OPENED -> CARD_ISSUED
 *     |            |
 *     +-> REJECTED +-> REJECTED
 * </pre>
 *
 * Every other move is refused with {@link IllegalStateTransitionException}.
 */
public enum ApplicationStatus {
    SUBMITTED,
    VALIDATED,
    APPROVED,
    ACCOUNT_OPENED,
    CARD_ISSUED,
    REJECTED;

    public Set<ApplicationStatus> allowedNext() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(VALIDATED, REJECTED);
            case VALIDATED -> EnumSet.of(APPROVED, REJECTED);
            case APPROVED -> EnumSet.of(ACCOUNT_OPENED);
            case ACCOUNT_OPENED -> EnumSet.of(CARD_ISSUED);
            case CARD_ISSUED, REJECTED -> EnumSet.noneOf(ApplicationStatus.class);
        };
    }

    public boolean canMoveTo(ApplicationStatus next) {
        return allowedNext().contains(next);
    }

    /** Returns {@code next} when the move is legal. Throws otherwise. */
    public ApplicationStatus moveTo(ApplicationStatus next) {
        if (!canMoveTo(next)) {
            throw new IllegalStateTransitionException(this, next);
        }
        return next;
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
