package io.github.kmadsdev.cardengine.domain.policy;

import io.github.kmadsdev.cardengine.domain.CardVariant;
import io.github.kmadsdev.cardengine.domain.Money;
import io.github.kmadsdev.cardengine.domain.policy.Reason.Effect;
import io.github.kmadsdev.cardengine.domain.policy.UpgradeDecision.Outcome;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every credit rule of the engine, in one file. Plain Java: no Spring, no database,
 * no clock. The same facts always give the same decision.
 *
 * <p>Read it top to bottom: constants, then the tables (variant bands, score bands,
 * validation rules, eligibility rules, upgrade rules), then the small amount of code
 * that walks the tables. To change a rule, change a table row and
 * {@link #VERSION}; every decision record stores the version that made it.
 *
 * <p>All numbers here are invented for a study project. They are not a real credit policy.
 */
public final class CreditPolicy {

    public static final String VERSION = "2026-11.1";

    // ---------------------------------------------------------------- constants

    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 90;
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 1000;
    public static final int SCORE_REJECT_BELOW = 300;
    public static final int SCORE_CREDIT_FROM = 500;

    /** A computed credit limit under this value is not offered; the client gets debit. */
    public static final BigDecimal MIN_CREDIT_LIMIT = Money.of("200.00");
    /** Highest limit this engine grants, any variant. */
    public static final BigDecimal MAX_CREDIT_LIMIT = Money.of("30000.00");
    /** Limits are rounded down to a multiple of this step. */
    public static final BigDecimal LIMIT_STEP = Money.of("10.00");

    public static final long UPGRADE_MIN_MONTHS_ACTIVE = 6;
    public static final int UPGRADE_MIN_STATEMENTS = 6;
    public static final BigDecimal UPGRADE_MIN_ON_TIME_RATIO = new BigDecimal("0.90");
    public static final BigDecimal UPGRADE_MIN_UTILIZATION = new BigDecimal("0.30");
    public static final BigDecimal BONUS_MIN_UTILIZATION = new BigDecimal("0.70");
    public static final BigDecimal BONUS_MULTIPLIER = new BigDecimal("1.25");
    public static final Duration UPGRADE_COOLDOWN = Duration.ofDays(30);

    // ---------------------------------------------------------------- variant bands

    /** Limit band per variant, both ends inclusive, in whole centavos. {@code max == null} means no upper end. */
    public record VariantBand(CardVariant variant, BigDecimal min, BigDecimal max) {
        boolean contains(BigDecimal limit) {
            return limit.compareTo(min) >= 0 && (max == null || limit.compareTo(max) <= 0);
        }
    }

    public static final List<VariantBand> VARIANT_BANDS = List.of(
            new VariantBand(CardVariant.DEBIT, Money.of("0.00"), Money.of("0.00")),
            new VariantBand(CardVariant.CREDIT_BASE, Money.of("0.01"), Money.of("2000.00")),
            new VariantBand(CardVariant.CREDIT_PLATINUM, Money.of("2000.01"), Money.of("8000.00")),
            new VariantBand(CardVariant.CREDIT_BLACK, Money.of("8000.01"), null));

    // ---------------------------------------------------------------- score bands

    /** Credit limit = declared monthly income x multiplier, for a score inside the band. */
    public record ScoreBand(int min, int max, BigDecimal incomeMultiplier) {
    }

    public static final List<ScoreBand> SCORE_BANDS = List.of(
            new ScoreBand(SCORE_CREDIT_FROM, 649, new BigDecimal("0.5")),
            new ScoreBand(650, 749, new BigDecimal("1.0")),
            new ScoreBand(750, 849, new BigDecimal("2.0")),
            new ScoreBand(850, MAX_SCORE, new BigDecimal("3.0")));

    // ---------------------------------------------------------------- acquisition: validation

    /** Runs before the bureau is called. Any REJECT here stops the application. */
    public static final List<Rule<ApplicantFacts>> VALIDATION_RULES = List.of(
            new Rule<>("INVALID_CPF", Effect.REJECT,
                    f -> !f.cpfValid(),
                    f -> "CPF check digits do not match"),
            new Rule<>("AGE_BELOW_MINIMUM", Effect.REJECT,
                    f -> f.age() < MIN_AGE,
                    f -> "age " + f.age() + " is below " + MIN_AGE),
            new Rule<>("AGE_ABOVE_MAXIMUM", Effect.REJECT,
                    f -> f.age() > MAX_AGE,
                    f -> "age " + f.age() + " is above " + MAX_AGE),
            new Rule<>("DUPLICATE_CUSTOMER", Effect.REJECT,
                    ApplicantFacts::existingCustomer,
                    f -> "a customer with this CPF already exists; use the upgrade flow"));

    // ---------------------------------------------------------------- acquisition: eligibility

    /** Facts plus the simulated bureau score, for the eligibility step. */
    public record ScoredApplicant(ApplicantFacts facts, int score) {
    }

    public static final List<Rule<ScoredApplicant>> ELIGIBILITY_RULES = List.of(
            new Rule<>("SCORE_TOO_LOW", Effect.REJECT,
                    a -> a.score() < SCORE_REJECT_BELOW,
                    a -> "score " + a.score() + " is below " + SCORE_REJECT_BELOW),
            new Rule<>("SCORE_DEBIT_ONLY", Effect.DEBIT_ONLY,
                    a -> a.score() >= SCORE_REJECT_BELOW && a.score() < SCORE_CREDIT_FROM,
                    a -> "score " + a.score() + " is below " + SCORE_CREDIT_FROM + ", credit is not offered"),
            new Rule<>("NO_DECLARED_INCOME", Effect.DEBIT_ONLY,
                    a -> a.facts().declaredMonthlyIncome().signum() == 0,
                    a -> "declared monthly income is 0.00"));

    // ---------------------------------------------------------------- upgrade

    public static final List<Rule<UpgradeFacts>> UPGRADE_RULES = List.of(
            new Rule<>("ALREADY_TOP_VARIANT", Effect.REJECT,
                    f -> f.currentVariant().next().isEmpty(),
                    f -> f.currentVariant() + " has no higher variant"),
            new Rule<>("ACCOUNT_TOO_NEW", Effect.REJECT,
                    f -> f.monthsActive() < UPGRADE_MIN_MONTHS_ACTIVE,
                    f -> f.monthsActive() + " months active, minimum is " + UPGRADE_MIN_MONTHS_ACTIVE),
            new Rule<>("SCORE_TOO_LOW", Effect.REJECT,
                    f -> f.score() < SCORE_CREDIT_FROM,
                    f -> "score " + f.score() + " is below " + SCORE_CREDIT_FROM),
            new Rule<>("NOT_ENOUGH_HISTORY", Effect.REJECT,
                    f -> f.currentVariant().isCredit() && f.statementCount() < UPGRADE_MIN_STATEMENTS,
                    f -> f.statementCount() + " statements, minimum is " + UPGRADE_MIN_STATEMENTS),
            new Rule<>("LATE_PAYMENTS", Effect.REJECT,
                    f -> f.currentVariant().isCredit() && f.onTimeRatio().compareTo(UPGRADE_MIN_ON_TIME_RATIO) < 0,
                    f -> "on-time ratio " + f.onTimeRatio().toPlainString() + " is below "
                            + UPGRADE_MIN_ON_TIME_RATIO.toPlainString()),
            new Rule<>("LOW_UTILIZATION", Effect.KEEP,
                    f -> f.currentVariant().isCredit() && f.utilization().compareTo(UPGRADE_MIN_UTILIZATION) < 0,
                    f -> "utilization " + f.utilization().toPlainString() + " is below "
                            + UPGRADE_MIN_UTILIZATION.toPlainString() + ", the current limit is enough"),
            new Rule<>("HISTORY_BONUS", Effect.INFO,
                    CreditPolicy::earnsHistoryBonus,
                    f -> "all statements on time and utilization at least "
                            + BONUS_MIN_UTILIZATION.toPlainString() + ": limit x " + BONUS_MULTIPLIER.toPlainString()));

    private CreditPolicy() {
    }

    // ---------------------------------------------------------------- evaluation

    /** The variant for a limit, from {@link #VARIANT_BANDS}. */
    public static CardVariant variantFor(BigDecimal limit) {
        BigDecimal amount = Money.of(limit);
        return VARIANT_BANDS.stream()
                .filter(b -> b.contains(amount))
                .findFirst()
                .orElseThrow()
                .variant();
    }

    public static VariantBand bandOf(CardVariant variant) {
        return VARIANT_BANDS.stream().filter(b -> b.variant() == variant).findFirst().orElseThrow();
    }

    /** Income multiplier for a score, or empty when the score gets no credit. */
    public static Optional<ScoreBand> scoreBandFor(int score) {
        requireScore(score);
        return SCORE_BANDS.stream().filter(b -> score >= b.min() && score <= b.max()).findFirst();
    }

    /** Validation step. An empty list means the applicant may go to the bureau. */
    public static List<Reason> validate(ApplicantFacts facts) {
        return applyAll(VALIDATION_RULES, facts);
    }

    /** Full acquisition decision: validation, eligibility, limit, variant. */
    public static AcquisitionDecision decideAcquisition(ApplicantFacts facts, int score) {
        requireScore(score);
        List<Reason> reasons = new ArrayList<>(validate(facts));
        if (has(reasons, Effect.REJECT)) {
            return new AcquisitionDecision(false, null, null, reasons, VERSION);
        }
        reasons.addAll(applyAll(ELIGIBILITY_RULES, new ScoredApplicant(facts, score)));
        if (has(reasons, Effect.REJECT)) {
            return new AcquisitionDecision(false, null, null, reasons, VERSION);
        }

        BigDecimal limit = Money.ZERO;
        if (!has(reasons, Effect.DEBIT_ONLY)) {
            limit = creditLimitFor(Money.of(facts.declaredMonthlyIncome()), score, BigDecimal.ONE, reasons);
            if (limit.compareTo(MIN_CREDIT_LIMIT) < 0) {
                reasons.add(new Reason("LIMIT_BELOW_MINIMUM", Effect.DEBIT_ONLY,
                        "computed limit " + limit.toPlainString() + " is below " + MIN_CREDIT_LIMIT.toPlainString()));
                limit = Money.ZERO;
            }
        }
        CardVariant variant = variantFor(limit);
        reasons.add(new Reason("VARIANT_ASSIGNED", Effect.INFO,
                "limit " + limit.toPlainString() + " falls in the " + variant + " band"));
        return new AcquisitionDecision(true, variant, limit, reasons, VERSION);
    }

    /**
     * Upgrade decision. It only ever proposes the next variant up: there is no path
     * to a lower variant, and no skipping of a variant.
     */
    public static UpgradeDecision decideUpgrade(UpgradeFacts facts) {
        requireScore(facts.score());
        List<Reason> reasons = new ArrayList<>(applyAll(UPGRADE_RULES, facts));
        if (has(reasons, Effect.REJECT)) {
            return new UpgradeDecision(Outcome.REJECTED, null, null, reasons, VERSION);
        }
        if (has(reasons, Effect.KEEP)) {
            return new UpgradeDecision(Outcome.KEPT, null, null, reasons, VERSION);
        }

        CardVariant target = facts.currentVariant().next().orElseThrow();
        VariantBand targetBand = bandOf(target);
        BigDecimal factor = earnsHistoryBonus(facts) ? BONUS_MULTIPLIER : BigDecimal.ONE;
        BigDecimal proposed = creditLimitFor(Money.of(facts.declaredMonthlyIncome()), facts.score(), factor, reasons);
        BigDecimal floor = targetBand.min().max(MIN_CREDIT_LIMIT);
        if (proposed.compareTo(floor) < 0) {
            reasons.add(new Reason("LIMIT_BELOW_NEXT_VARIANT", Effect.KEEP,
                    "proposed limit " + proposed.toPlainString() + " does not reach " + target
                            + " (from " + floor.toPlainString() + ")"));
            return new UpgradeDecision(Outcome.KEPT, null, null, reasons, VERSION);
        }
        BigDecimal newLimit = targetBand.max() == null ? proposed : proposed.min(targetBand.max());
        reasons.add(new Reason("UPGRADE_TO_NEXT_VARIANT", Effect.INFO,
                facts.currentVariant() + " -> " + target + ", limit "
                        + Money.of(facts.currentLimit()).toPlainString() + " -> " + newLimit.toPlainString()));
        return new UpgradeDecision(Outcome.APPROVED, target, newLimit, reasons, VERSION);
    }

    /** When the next upgrade request is allowed, given the time of the last one. */
    public static Instant cooldownEndsAt(Instant lastRequestAt) {
        return lastRequestAt.plus(UPGRADE_COOLDOWN);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean earnsHistoryBonus(UpgradeFacts f) {
        return f.currentVariant().isCredit()
                && f.statementCount() >= UPGRADE_MIN_STATEMENTS
                && f.onTimeRatio().compareTo(BigDecimal.ONE) >= 0
                && f.utilization().compareTo(BONUS_MIN_UTILIZATION) >= 0;
    }

    /** income x band multiplier x factor, rounded down to {@link #LIMIT_STEP}, capped at {@link #MAX_CREDIT_LIMIT}. */
    private static BigDecimal creditLimitFor(BigDecimal income, int score, BigDecimal factor, List<Reason> reasons) {
        Optional<ScoreBand> band = scoreBandFor(score);
        if (band.isEmpty()) {
            return Money.ZERO;
        }
        BigDecimal raw = income.multiply(band.get().incomeMultiplier()).multiply(factor);
        BigDecimal limit = Money.floorTo(raw.setScale(2, RoundingMode.DOWN), LIMIT_STEP);
        reasons.add(new Reason("SCORE_BAND", Effect.INFO,
                "score " + score + " in band " + band.get().min() + "-" + band.get().max()
                        + ": limit = income " + income.toPlainString() + " x "
                        + band.get().incomeMultiplier().toPlainString()
                        + (factor.compareTo(BigDecimal.ONE) == 0 ? "" : " x " + factor.toPlainString())
                        + " = " + limit.toPlainString()));
        if (limit.compareTo(MAX_CREDIT_LIMIT) > 0) {
            reasons.add(new Reason("LIMIT_CAPPED", Effect.INFO,
                    "limit " + limit.toPlainString() + " capped at " + MAX_CREDIT_LIMIT.toPlainString()));
            return MAX_CREDIT_LIMIT;
        }
        return limit;
    }

    private static <F> List<Reason> applyAll(List<Rule<F>> rules, F facts) {
        List<Reason> fired = new ArrayList<>();
        for (Rule<F> rule : rules) {
            rule.apply(facts).ifPresent(fired::add);
        }
        return fired;
    }

    private static boolean has(List<Reason> reasons, Effect effect) {
        return reasons.stream().anyMatch(r -> r.effect() == effect);
    }

    private static void requireScore(int score) {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException("score must be between " + MIN_SCORE + " and " + MAX_SCORE);
        }
    }
}
