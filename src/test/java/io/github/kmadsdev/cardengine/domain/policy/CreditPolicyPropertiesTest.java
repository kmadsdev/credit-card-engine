package io.github.kmadsdev.cardengine.domain.policy;

import io.github.kmadsdev.cardengine.domain.CardVariant;
import io.github.kmadsdev.cardengine.domain.policy.UpgradeDecision.Outcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-style checks: thousands of generated inputs, a few rules that must hold for
 * every one of them. The seed is fixed, so a failure is reproducible. No extra
 * library: a loop and AssertJ are enough at this size.
 */
class CreditPolicyPropertiesTest {

    private static final int RUNS = 5_000;
    private static final long SEED = 20261113L;

    private static BigDecimal amount(SplittableRandom rnd, int maxReais) {
        return BigDecimal.valueOf(rnd.nextLong(maxReais * 100L + 1), 2);
    }

    @Test
    void acquisitionInvariants() {
        SplittableRandom rnd = new SplittableRandom(SEED);
        for (int i = 0; i < RUNS; i++) {
            var facts = new ApplicantFacts(rnd.nextInt(10) > 0, rnd.nextInt(10, 100), rnd.nextInt(20) == 0,
                    amount(rnd, 40_000));
            int score = rnd.nextInt(0, 1001);

            AcquisitionDecision d = CreditPolicy.decideAcquisition(facts, score);

            assertThat(CreditPolicy.decideAcquisition(facts, score)).as("deterministic").isEqualTo(d);
            assertThat(d.reasons()).as("always explained").isNotEmpty();
            if (!d.approved()) {
                assertThat(d.reasons()).anyMatch(r -> r.effect() == Reason.Effect.REJECT);
                continue;
            }
            assertThat(facts.cpfValid()).isTrue();
            assertThat(facts.existingCustomer()).isFalse();
            assertThat(facts.age()).isBetween(CreditPolicy.MIN_AGE, CreditPolicy.MAX_AGE);
            assertThat(score).isGreaterThanOrEqualTo(CreditPolicy.SCORE_REJECT_BELOW);
            assertThat(d.limit().scale()).isEqualTo(2);
            assertThat(d.variant()).isEqualTo(CreditPolicy.variantFor(d.limit()));
            assertThat(d.limit()).isLessThanOrEqualTo(CreditPolicy.MAX_CREDIT_LIMIT);
            assertThat(d.limit().remainder(CreditPolicy.LIMIT_STEP)).isEqualByComparingTo("0");
            if (d.variant().isCredit()) {
                assertThat(d.limit()).isGreaterThanOrEqualTo(CreditPolicy.MIN_CREDIT_LIMIT);
                assertThat(score).isGreaterThanOrEqualTo(CreditPolicy.SCORE_CREDIT_FROM);
            }
        }
    }

    @Test
    void upgradeInvariants() {
        SplittableRandom rnd = new SplittableRandom(SEED + 1);
        CardVariant[] variants = CardVariant.values();
        int approved = 0;
        for (int i = 0; i < RUNS; i++) {
            CardVariant current = variants[rnd.nextInt(variants.length)];
            var band = CreditPolicy.bandOf(current);
            BigDecimal limit = current.isCredit()
                    ? band.min().add(amount(rnd, 1000)).min(band.max() == null ? CreditPolicy.MAX_CREDIT_LIMIT : band.max())
                    : BigDecimal.ZERO.setScale(2);
            var facts = new UpgradeFacts(current, limit, rnd.nextLong(0, 60), rnd.nextInt(0, 30),
                    BigDecimal.valueOf(rnd.nextInt(0, 101), 2), BigDecimal.valueOf(rnd.nextInt(0, 121), 2),
                    amount(rnd, 20_000), rnd.nextInt(0, 1001));

            UpgradeDecision d = CreditPolicy.decideUpgrade(facts);

            assertThat(CreditPolicy.decideUpgrade(facts)).as("deterministic").isEqualTo(d);
            assertThat(d.reasons()).isNotEmpty();
            if (d.outcome() != Outcome.APPROVED) {
                assertThat(d.targetVariant()).isNull();
                assertThat(d.newLimit()).isNull();
                continue;
            }
            approved++;
            assertThat(d.targetVariant()).as("exactly one step up, never down")
                    .isEqualTo(current.next().orElseThrow());
            assertThat(CreditPolicy.variantFor(d.newLimit())).isEqualTo(d.targetVariant());
            assertThat(d.newLimit()).isGreaterThan(limit);
            assertThat(facts.monthsActive()).isGreaterThanOrEqualTo(CreditPolicy.UPGRADE_MIN_MONTHS_ACTIVE);
            if (current.isCredit()) {
                assertThat(facts.onTimeRatio()).isGreaterThanOrEqualTo(CreditPolicy.UPGRADE_MIN_ON_TIME_RATIO);
            }
        }
        assertThat(approved).as("the generator reaches the approval path").isGreaterThan(50);
    }
}
