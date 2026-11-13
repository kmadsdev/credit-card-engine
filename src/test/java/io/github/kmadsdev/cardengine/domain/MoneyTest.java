package io.github.kmadsdev.cardengine.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({"0, 0.00", "10, 10.00", "2000.1, 2000.10", "8000.01, 8000.01", "3.000, 3.00"})
    void normalizesToTwoDecimals(String in, String out) {
        assertThat(Money.of(in)).isEqualTo(new BigDecimal(out));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "2000.001", "0.005"})
    void refusesNegativeOrSubCentavo(String in) {
        assertThatThrownBy(() -> Money.of(in))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("invalid-amount");
    }

    @Test
    void refusesNull() {
        assertThatThrownBy(() -> Money.of((BigDecimal) null)).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @CsvSource({"1999.99, 1990.00", "2000.00, 2000.00", "9.99, 0.00", "10.00, 10.00"})
    void floorsToStep(String in, String out) {
        assertThat(Money.floorTo(new BigDecimal(in), new BigDecimal("10.00"))).isEqualTo(new BigDecimal(out));
    }
}
