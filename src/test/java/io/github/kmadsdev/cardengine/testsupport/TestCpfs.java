package io.github.kmadsdev.cardengine.testsupport;

import java.util.random.RandomGenerator;

/**
 * Generates synthetic CPFs for tests. No fixed CPF is written anywhere in this
 * repository: every test makes a new one at run time. The check-digit algorithm is
 * written again here (mod 11 with the "x10" form) on purpose, so the tests do not
 * ask the production code to check itself.
 */
public final class TestCpfs {

    private TestCpfs() {
    }

    public static String random() {
        return random(RandomGenerator.getDefault());
    }

    public static String random(RandomGenerator rnd) {
        String base;
        do {
            StringBuilder sb = new StringBuilder(9);
            for (int i = 0; i < 9; i++) {
                sb.append(rnd.nextInt(10));
            }
            base = sb.toString();
        } while (base.chars().distinct().count() == 1);
        int d1 = digit(base, 10);
        int d2 = digit(base + d1, 11);
        return base + d1 + d2;
    }

    /** Same CPF with the last check digit changed, so it fails validation. */
    public static String withWrongCheckDigit(String cpf) {
        char last = cpf.charAt(10);
        char wrong = last == '9' ? '0' : (char) (last + 1);
        return cpf.substring(0, 10) + wrong;
    }

    public static String formatted(String cpf) {
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    private static int digit(String s, int firstWeight) {
        int sum = 0;
        for (int i = 0; i < s.length(); i++) {
            sum += Character.getNumericValue(s.charAt(i)) * (firstWeight - i);
        }
        int mod = (sum * 10) % 11;
        return mod == 10 ? 0 : mod;
    }
}
