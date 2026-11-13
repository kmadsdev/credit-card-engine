package io.github.kmadsdev.cardengine.domain;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class LuhnTest {

    @ParameterizedTest
    @CsvSource({
            // payload, check digit (worked by hand: 7992739871 is the classic textbook example)
            "7992739871, 3",
            "0, 0",
            "1, 8",
            "99000000000000, 2",
    })
    void computesCheckDigit(String payload, int expected) {
        assertThat(Luhn.checkDigit(payload)).isEqualTo(expected);
        assertThat(Luhn.isValid(payload + expected)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"7", "79927398710", "7992739871a"})
    void rejectsInvalid(String digits) {
        assertThat(Luhn.isValid(digits)).isFalse();
    }

    @RepeatedTest(500)
    void generatedTestPanIsLuhnValidWithTestPrefix() {
        String pan = Luhn.generateTestPan(RandomGenerator.getDefault());
        assertThat(pan).hasSize(16).startsWith(Luhn.TEST_PREFIX);
        assertThat(Luhn.isValid(pan)).isTrue();
    }

    @Test
    void anySingleDigitChangeBreaksTheCheck() {
        String pan = Luhn.generateTestPan(RandomGenerator.getDefault());
        for (int i = 0; i < pan.length(); i++) {
            int d = pan.charAt(i) - '0';
            String changed = pan.substring(0, i) + ((d + 1) % 10) + pan.substring(i + 1);
            assertThat(Luhn.isValid(changed)).as("digit %d changed", i).isFalse();
        }
    }
}
