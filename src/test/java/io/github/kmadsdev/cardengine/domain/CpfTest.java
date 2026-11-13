package io.github.kmadsdev.cardengine.domain;

import io.github.kmadsdev.cardengine.testsupport.TestCpfs;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CpfTest {

    @RepeatedTest(200)
    void acceptsGeneratedCpfWithAndWithoutPunctuation() {
        String cpf = TestCpfs.random();
        assertThat(Cpf.isValid(cpf)).isTrue();
        assertThat(Cpf.isValid(TestCpfs.formatted(cpf))).isTrue();
    }

    @RepeatedTest(200)
    void rejectsWrongCheckDigit() {
        assertThat(Cpf.isValid(TestCpfs.withWrongCheckDigit(TestCpfs.random()))).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234567890", "123456789012", "1234567890a", "00000000000", "99999999999"})
    void rejectsBadShapeAndRepeatedDigits(String raw) {
        assertThat(Cpf.isValid(raw)).isFalse();
    }

    @Test
    void masksAllButCheckDigits() {
        String cpf = TestCpfs.random();
        assertThat(Cpf.mask(cpf)).isEqualTo("***.***.***-" + cpf.substring(9));
        assertThat(Cpf.mask(TestCpfs.formatted(cpf))).doesNotContain(cpf.substring(0, 9));
        assertThat(Cpf.mask("123")).isEqualTo("***.***.***-**");
    }
}
