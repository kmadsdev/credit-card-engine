package io.github.kmadsdev.cardengine.domain.policy;

import java.math.BigDecimal;

/**
 * What the acquisition policy knows about a new applicant. The service collects it;
 * the policy only reads it.
 *
 * @param cpfValid              the CPF passed the check-digit test
 * @param age                   full years on the day of the application
 * @param existingCustomer      a customer with this CPF already exists
 * @param declaredMonthlyIncome income the applicant declared, in BRL (not verified)
 */
public record ApplicantFacts(boolean cpfValid, int age, boolean existingCustomer, BigDecimal declaredMonthlyIncome) {
}
