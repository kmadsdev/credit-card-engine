package io.github.kmadsdev.cardengine.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request bodies. Bean Validation checks the shape only. The CPF check digits, the
 * age and duplicates are business rules: they produce a REJECTED application with a
 * decision record, not a 400, so the refusal is audited.
 */
public final class Requests {

    private Requests() {
    }

    public record CreateApplication(
            @Schema(description = "CPF, 11 digits, with or without punctuation", example = "000.000.000-00")
            @NotBlank @Pattern(regexp = "\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}", message = "must be 11 digits, optionally punctuated")
            String cpf,
            @NotBlank @Size(max = 120)
            String fullName,
            @NotNull @Past
            LocalDate birthDate,
            @Schema(description = "Declared monthly income in BRL. Not verified.", example = "4500.00")
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2)
            BigDecimal declaredMonthlyIncome) {

        /** Never print the CPF or the name. */
        @Override
        public String toString() {
            return "CreateApplication[birthDate=" + birthDate + ", income=" + declaredMonthlyIncome + "]";
        }
    }

    public record CreateUpgradeRequest(
            @Schema(description = "Optional new declared monthly income in BRL", example = "9000.00")
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2)
            BigDecimal declaredMonthlyIncome) {
    }
}
