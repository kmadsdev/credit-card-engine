package io.github.kmadsdev.cardengine.domain.policy;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One row of a rule table: when {@code firesWhen} is true for the facts, the rule
 * adds a {@link Reason} with {@code effect} to the decision.
 */
public record Rule<F>(String code, Reason.Effect effect, Predicate<F> firesWhen, Function<F, String> detail) {

    public Optional<Reason> apply(F facts) {
        return firesWhen.test(facts)
                ? Optional.of(new Reason(code, effect, detail.apply(facts)))
                : Optional.empty();
    }
}
