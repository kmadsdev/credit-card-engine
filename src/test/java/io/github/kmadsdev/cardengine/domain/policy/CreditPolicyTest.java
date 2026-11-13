package io.github.kmadsdev.cardengine.domain.policy;

import io.github.kmadsdev.cardengine.domain.CardVariant;
import io.github.kmadsdev.cardengine.domain.DomainException;
import io.github.kmadsdev.cardengine.domain.policy.CreditPolicy.VariantBand;
import io.github.kmadsdev.cardengine.domain.policy.UpgradeDecision.Outcome;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static io.github.kmadsdev.cardengine.domain.CardVariant.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditPolicyTest {

    private static BigDecimal brl(String v) {
        return new BigDecimal(v);
    }

    private static ApplicantFacts applicant(int age, String income) {
        return new ApplicantFacts(true, age, false, brl(income));
    }

    private static List<String> codes(List<Reason> reasons) {
        return reasons.stream().map(Reason::code).toList();
    }

    @Nested
    class VariantBands {

        @ParameterizedTest(name = "limit {0} -> {1}")
        @CsvSource({
                "0.00, DEBIT",
                "0.01, CREDIT_BASE",
                "1999.99, CREDIT_BASE",
                "2000.00, CREDIT_BASE",
                "2000.01, CREDIT_PLATINUM",
                "7999.99, CREDIT_PLATINUM",
                "8000.00, CREDIT_PLATINUM",
                "8000.01, CREDIT_BLACK",
                "30000.00, CREDIT_BLACK",
                "2000, CREDIT_BASE",
                "8000.0, CREDIT_PLATINUM",
        })
        void boundaries(String limit, CardVariant expected) {
            assertThat(CreditPolicy.variantFor(brl(limit))).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"-0.01", "2000.001", "8000.005"})
        void refusesNegativeAndSubCentavoLimits(String limit) {
            assertThatThrownBy(() -> CreditPolicy.variantFor(brl(limit))).isInstanceOf(DomainException.class);
        }

        @Test
        void bandsCoverEveryCentavoWithoutGapOrOverlap() {
            List<VariantBand> bands = CreditPolicy.VARIANT_BANDS;
            assertThat(bands.getFirst().min()).isEqualByComparingTo("0.00");
            for (int i = 1; i < bands.size(); i++) {
                assertThat(bands.get(i).min()).isEqualTo(bands.get(i - 1).max().add(brl("0.01")));
                assertThat(bands.get(i).variant()).isEqualTo(bands.get(i - 1).variant().next().orElseThrow());
            }
            assertThat(bands.getLast().max()).isNull();
        }

        @Test
        void scoreBandsAreContiguousUpToMaxScore() {
            var bands = CreditPolicy.SCORE_BANDS;
            assertThat(bands.getFirst().min()).isEqualTo(CreditPolicy.SCORE_CREDIT_FROM);
            for (int i = 1; i < bands.size(); i++) {
                assertThat(bands.get(i).min()).isEqualTo(bands.get(i - 1).max() + 1);
            }
            assertThat(bands.getLast().max()).isEqualTo(CreditPolicy.MAX_SCORE);
            assertThat(CreditPolicy.scoreBandFor(499)).isEmpty();
        }
    }

    @Nested
    class Validation {

        @ParameterizedTest(name = "age {0} valid={1}")
        @CsvSource({"17, false", "18, true", "90, true", "91, false"})
        void ageLimits(int age, boolean valid) {
            assertThat(CreditPolicy.validate(applicant(age, "3000.00")).isEmpty()).isEqualTo(valid);
        }

        @Test
        void collectsEveryFailedRuleNotOnlyTheFirst() {
            var reasons = CreditPolicy.validate(new ApplicantFacts(false, 16, true, brl("1000.00")));
            assertThat(codes(reasons)).containsExactly("INVALID_CPF", "AGE_BELOW_MINIMUM", "DUPLICATE_CUSTOMER");
            assertThat(reasons).allMatch(r -> r.effect() == Reason.Effect.REJECT);
        }

        @Test
        void rejectedValidationStopsBeforeEligibility() {
            var decision = CreditPolicy.decideAcquisition(new ApplicantFacts(false, 30, false, brl("5000.00")), 900);
            assertThat(decision.approved()).isFalse();
            assertThat(codes(decision.reasons())).containsExactly("INVALID_CPF");
            assertThat(decision.variant()).isNull();
        }
    }

    @Nested
    class Acquisition {

        @ParameterizedTest(name = "score {0}, income {1} -> approved={2} {3} {4}")
        @CsvSource({
                "0,    5000.00,  false, ,                ,         SCORE_TOO_LOW",
                "299,  5000.00,  false, ,                ,         SCORE_TOO_LOW",
                "300,  5000.00,  true,  DEBIT,           0.00,     SCORE_DEBIT_ONLY",
                "499,  5000.00,  true,  DEBIT,           0.00,     SCORE_DEBIT_ONLY",
                "500,  5000.00,  true,  CREDIT_PLATINUM, 2500.00,  SCORE_BAND",
                "500,  399.99,   true,  DEBIT,           0.00,     LIMIT_BELOW_MINIMUM",
                "500,  400.00,   true,  CREDIT_BASE,     200.00,   SCORE_BAND",
                "650,  2000.00,  true,  CREDIT_BASE,     2000.00,  SCORE_BAND",
                "650,  2009.99,  true,  CREDIT_BASE,     2000.00,  SCORE_BAND",
                "650,  2010.00,  true,  CREDIT_PLATINUM, 2010.00,  SCORE_BAND",
                "750,  4000.00,  true,  CREDIT_PLATINUM, 8000.00,  SCORE_BAND",
                "750,  4005.00,  true,  CREDIT_BLACK,    8010.00,  SCORE_BAND",
                "850,  12000.00, true,  CREDIT_BLACK,    30000.00, LIMIT_CAPPED",
                "1000, 0.00,     true,  DEBIT,           0.00,     NO_DECLARED_INCOME",
        })
        void decisionTable(int score, String income, boolean approved, CardVariant variant, String limit, String reason) {
            AcquisitionDecision d = CreditPolicy.decideAcquisition(applicant(35, income), score);

            assertThat(d.approved()).isEqualTo(approved);
            assertThat(d.variant()).isEqualTo(variant);
            if (limit == null) {
                assertThat(d.limit()).isNull();
            } else {
                assertThat(d.limit()).isEqualTo(brl(limit));
            }
            assertThat(codes(d.reasons())).contains(reason);
            assertThat(d.policyVersion()).isEqualTo(CreditPolicy.VERSION);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 1001})
        void refusesScoreOutsideRange(int score) {
            assertThatThrownBy(() -> CreditPolicy.decideAcquisition(applicant(30, "1000.00"), score))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void approvalExplainsTheLimitComputation() {
            var d = CreditPolicy.decideAcquisition(applicant(30, "3000.00"), 760);
            assertThat(d.reasons()).extracting(Reason::detail)
                    .anySatisfy(t -> assertThat(t).contains("3000.00 x 2.0 = 6000.00"))
                    .anySatisfy(t -> assertThat(t).contains("CREDIT_PLATINUM band"));
        }
    }

    @Nested
    class Upgrade {

        /** A healthy CREDIT_BASE account; each test changes one thing. */
        private UpgradeFacts base(CardVariant v, String limit, String onTime, String util, String income, int score) {
            return new UpgradeFacts(v, brl(limit), 12, 12, brl(onTime), brl(util), brl(income), score);
        }

        @Test
        void topVariantIsRejected() {
            var d = CreditPolicy.decideUpgrade(base(CREDIT_BLACK, "9000.00", "1.00", "0.90", "20000.00", 900));
            assertThat(d.outcome()).isEqualTo(Outcome.REJECTED);
            assertThat(codes(d.reasons())).contains("ALREADY_TOP_VARIANT");
        }

        @ParameterizedTest(name = "months={0} statements={1} onTime={2} score={3} -> {4}")
        @CsvSource({
                "5,  12, 1.00, 700, ACCOUNT_TOO_NEW",
                "12, 5,  1.00, 700, NOT_ENOUGH_HISTORY",
                "12, 12, 0.89, 700, LATE_PAYMENTS",
                "12, 12, 1.00, 499, SCORE_TOO_LOW",
        })
        void hardRejections(long months, int statements, String onTime, int score, String code) {
            var facts = new UpgradeFacts(CREDIT_BASE, brl("2000.00"), months, statements,
                    brl(onTime), brl("0.80"), brl("5000.00"), score);
            var d = CreditPolicy.decideUpgrade(facts);
            assertThat(d.outcome()).isEqualTo(Outcome.REJECTED);
            assertThat(codes(d.reasons())).contains(code);
            assertThat(d.targetVariant()).isNull();
        }

        @ParameterizedTest(name = "{0} limit {1} onTime {2} util {3} income {4} score {5} -> {6} {7} {8}")
        @CsvSource({
                // on-time ratio exactly at the minimum passes
                "CREDIT_BASE,     2000.00, 0.90, 0.80, 4000.00,  700, APPROVED, CREDIT_PLATINUM, 4000.00",
                "CREDIT_BASE,     2000.00, 0.95, 0.29, 4000.00,  700, KEPT,     ,                ",
                "CREDIT_BASE,     2000.00, 0.95, 0.30, 4000.00,  700, APPROVED, CREDIT_PLATINUM, 4000.00",
                "CREDIT_BASE,     2000.00, 0.95, 0.80, 1500.00,  700, KEPT,     ,                ",
                // never skips a variant: capped at the PLATINUM top
                "CREDIT_BASE,     2000.00, 0.95, 0.80, 10000.00, 860, APPROVED, CREDIT_PLATINUM, 8000.00",
                // history bonus: 3500 x 2.0 x 1.25 = 8750
                "CREDIT_PLATINUM, 8000.00, 1.00, 0.80, 3500.00,  760, APPROVED, CREDIT_BLACK,    8750.00",
                // no bonus (utilization 0.60): 7000 does not reach BLACK
                "CREDIT_PLATINUM, 8000.00, 1.00, 0.60, 3500.00,  760, KEPT,     ,                ",
                // debit accounts skip payment rules
                "DEBIT,           0.00,    1.00, 0.00, 3000.00,  600, APPROVED, CREDIT_BASE,     1500.00",
                "DEBIT,           0.00,    1.00, 0.00, 300.00,   600, KEPT,     ,                ",
        })
        void decisionTable(CardVariant variant, String limit, String onTime, String util, String income, int score,
                           Outcome outcome, CardVariant target, String newLimit) {
            var d = CreditPolicy.decideUpgrade(base(variant, limit, onTime, util, income, score));
            assertThat(d.outcome()).isEqualTo(outcome);
            assertThat(d.targetVariant()).isEqualTo(target);
            if (newLimit == null) {
                assertThat(d.newLimit()).isNull();
            } else {
                assertThat(d.newLimit()).isEqualTo(brl(newLimit));
            }
            assertThat(d.reasons()).isNotEmpty();
        }

        @Test
        void debitAccountWithoutStatementsIsNotBlockedByHistoryRules() {
            var facts = new UpgradeFacts(DEBIT, brl("0.00"), 6, 0, BigDecimal.ONE, BigDecimal.ZERO, brl("3000.00"), 600);
            assertThat(codes(CreditPolicy.decideUpgrade(facts).reasons()))
                    .doesNotContain("NOT_ENOUGH_HISTORY", "LATE_PAYMENTS", "LOW_UTILIZATION");
        }

        @Test
        void cooldownIsThirtyDays() {
            Instant last = Instant.parse("2026-01-01T12:00:00Z");
            assertThat(CreditPolicy.cooldownEndsAt(last)).isEqualTo(last.plus(Duration.ofDays(30)));
        }
    }
}
