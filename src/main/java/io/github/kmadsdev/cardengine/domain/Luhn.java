package io.github.kmadsdev.cardengine.domain;

import java.util.random.RandomGenerator;

/**
 * Luhn (mod 10) check digit, used by card numbers.
 *
 * <p>{@link #generateTestPan} builds a number that passes the Luhn check. It uses the
 * prefix {@link #TEST_PREFIX}, which this project uses only for synthetic numbers.
 * The engine never stores or logs the full number; see ADR 0004.
 */
public final class Luhn {

    /** Synthetic prefix. Not a prefix that this project links to any real card network. */
    public static final String TEST_PREFIX = "990000";

    private Luhn() {
    }

    public static boolean isValid(String digits) {
        if (digits == null || digits.length() < 2 || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        int expected = checkDigit(digits.substring(0, digits.length() - 1));
        return digits.charAt(digits.length() - 1) - '0' == expected;
    }

    /** The digit to append to {@code payload} so the whole number passes the Luhn check. */
    public static int checkDigit(String payload) {
        int sum = 0;
        boolean doubleIt = true; // the digit next to the check digit is doubled
        for (int i = payload.length() - 1; i >= 0; i--) {
            int d = payload.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return (10 - sum % 10) % 10;
    }

    /** A 16-digit Luhn-valid number that starts with {@link #TEST_PREFIX}. */
    public static String generateTestPan(RandomGenerator random) {
        StringBuilder sb = new StringBuilder(TEST_PREFIX);
        while (sb.length() < 15) {
            sb.append(random.nextInt(10));
        }
        sb.append(checkDigit(sb.toString()));
        return sb.toString();
    }
}
