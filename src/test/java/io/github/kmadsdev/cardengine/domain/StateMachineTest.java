package io.github.kmadsdev.cardengine.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static io.github.kmadsdev.cardengine.domain.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks every (from, to) pair of both state machines against an expected table that
 * is written out here by hand. A change in the enum that is not also made here fails.
 */
class StateMachineTest {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> APPLICATION = Map.of(
            SUBMITTED, EnumSet.of(VALIDATED, REJECTED),
            VALIDATED, EnumSet.of(APPROVED, REJECTED),
            APPROVED, EnumSet.of(ACCOUNT_OPENED),
            ACCOUNT_OPENED, EnumSet.of(CARD_ISSUED),
            CARD_ISSUED, EnumSet.noneOf(ApplicationStatus.class),
            REJECTED, EnumSet.noneOf(ApplicationStatus.class));

    private static final Map<UpgradeStatus, Set<UpgradeStatus>> UPGRADE = Map.of(
            UpgradeStatus.REQUESTED, EnumSet.of(UpgradeStatus.APPROVED, UpgradeStatus.KEPT, UpgradeStatus.REJECTED),
            UpgradeStatus.APPROVED, EnumSet.of(UpgradeStatus.APPLIED),
            UpgradeStatus.APPLIED, EnumSet.noneOf(UpgradeStatus.class),
            UpgradeStatus.KEPT, EnumSet.noneOf(UpgradeStatus.class),
            UpgradeStatus.REJECTED, EnumSet.noneOf(UpgradeStatus.class));

    static Stream<Arguments> applicationPairs() {
        return Arrays.stream(ApplicationStatus.values())
                .flatMap(from -> Arrays.stream(ApplicationStatus.values()).map(to -> Arguments.of(from, to)));
    }

    static Stream<Arguments> upgradePairs() {
        return Arrays.stream(UpgradeStatus.values())
                .flatMap(from -> Arrays.stream(UpgradeStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("applicationPairs")
    void applicationTransitions(ApplicationStatus from, ApplicationStatus to) {
        boolean legal = APPLICATION.get(from).contains(to);
        assertThat(from.canMoveTo(to)).isEqualTo(legal);
        if (legal) {
            assertThat(from.moveTo(to)).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> from.moveTo(to))
                    .isInstanceOf(IllegalStateTransitionException.class)
                    .hasMessageContaining(from.name()).hasMessageContaining(to.name());
        }
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("upgradePairs")
    void upgradeTransitions(UpgradeStatus from, UpgradeStatus to) {
        boolean legal = UPGRADE.get(from).contains(to);
        assertThat(from.canMoveTo(to)).isEqualTo(legal);
        if (legal) {
            assertThat(from.moveTo(to)).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> from.moveTo(to)).isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Test
    void terminalStates() {
        assertThat(Arrays.stream(ApplicationStatus.values()).filter(ApplicationStatus::isTerminal))
                .containsExactlyInAnyOrder(CARD_ISSUED, REJECTED);
        assertThat(Arrays.stream(UpgradeStatus.values()).filter(UpgradeStatus::isTerminal))
                .containsExactlyInAnyOrder(UpgradeStatus.APPLIED, UpgradeStatus.KEPT, UpgradeStatus.REJECTED);
    }

    @Test
    void variantOrder() {
        assertThat(CardVariant.DEBIT.next()).contains(CardVariant.CREDIT_BASE);
        assertThat(CardVariant.CREDIT_PLATINUM.next()).contains(CardVariant.CREDIT_BLACK);
        assertThat(CardVariant.CREDIT_BLACK.next()).isEmpty();
        assertThat(CardVariant.CREDIT_BLACK.isAbove(CardVariant.DEBIT)).isTrue();
        assertThat(CardVariant.DEBIT.isCredit()).isFalse();
    }
}
