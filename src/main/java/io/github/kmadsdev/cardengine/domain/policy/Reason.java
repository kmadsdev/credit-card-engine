package io.github.kmadsdev.cardengine.domain.policy;

/**
 * One line of a decision record: which rule fired and what it did.
 *
 * @param code   stable rule code, for example {@code SCORE_TOO_LOW}
 * @param effect what the rule did to the decision
 * @param detail human text with the numbers that made the rule fire
 */
public record Reason(String code, Effect effect, String detail) {

    public enum Effect {
        /** Stops the flow. The application or upgrade is rejected. */
        REJECT,
        /** Acquisition only: the client gets a debit card (limit 0), not credit. */
        DEBIT_ONLY,
        /** Upgrade only: the account stays as it is. */
        KEEP,
        /** Does not change the outcome. Records how a number was computed. */
        INFO
    }
}
