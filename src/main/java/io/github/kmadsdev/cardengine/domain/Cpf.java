package io.github.kmadsdev.cardengine.domain;

/**
 * Brazilian individual taxpayer number (CPF) checks.
 *
 * <p>The application stores the CPF as 11 digits. This class only answers questions
 * about a value (shape, check digits) and masks it for display. It never logs.
 */
public final class Cpf {

    private static final int LENGTH = 11;

    private Cpf() {
    }

    /** Removes the usual punctuation: "123.456.789-09" becomes "12345678909". */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace(".", "").replace("-", "").strip();
    }

    /** True when the value has 11 digits and both check digits match. */
    public static boolean isValid(String raw) {
        String digits = normalize(raw);
        if (digits.length() != LENGTH || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        // 000.000.000-00, 111.111.111-11 and so on pass the arithmetic, but they are not issued.
        if (digits.chars().distinct().count() == 1) {
            return false;
        }
        int first = checkDigit(digits.substring(0, 9));
        int second = checkDigit(digits.substring(0, 9) + first);
        return digits.charAt(9) - '0' == first && digits.charAt(10) - '0' == second;
    }

    /** Only the two check digits stay visible: {@code ***.***.***-09}. */
    public static String mask(String raw) {
        String digits = normalize(raw);
        if (digits.length() != LENGTH) {
            return "***.***.***-**";
        }
        return "***.***.***-" + digits.substring(9);
    }

    /** Weights go from length + 1 down to 2. A remainder below 2 gives digit 0. */
    static int checkDigit(String base) {
        int sum = 0;
        int weight = base.length() + 1;
        for (int i = 0; i < base.length(); i++) {
            sum += (base.charAt(i) - '0') * weight--;
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
