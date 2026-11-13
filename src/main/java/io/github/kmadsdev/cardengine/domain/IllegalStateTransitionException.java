package io.github.kmadsdev.cardengine.domain;

public class IllegalStateTransitionException extends DomainException {

    public IllegalStateTransitionException(Enum<?> from, Enum<?> to) {
        super("illegal-state-transition",
                "Transition " + from.name() + " -> " + to.name() + " is not allowed");
    }
}
