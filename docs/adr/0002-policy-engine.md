# ADR 0002: One table-driven policy engine

Status: accepted (2026-11-13)

## Context

Credit rules change often and must be explained to auditors and to the client. When
rules are spread over services and controllers, nobody can answer "why was this
person rejected?" from the code.

## Decision

All rules live in `domain/policy/CreditPolicy.java`:

- `VARIANT_BANDS`: limit band of each variant, both ends inclusive, in centavos.
- `SCORE_BANDS`: income multiplier per simulated score band.
- `VALIDATION_RULES`, `ELIGIBILITY_RULES`, `UPGRADE_RULES`: rows of
  `(code, effect, firesWhen, detail)`.

The engine walks the tables. Every rule that fires adds a `Reason` with a stable code,
an effect (`REJECT`, `DEBIT_ONLY`, `KEEP`, `INFO`) and a detail text with the numbers.
Precedence is fixed: any `REJECT` wins, then `KEEP` / `DEBIT_ONLY`, then the limit
calculation. All rules are evaluated, so a rejection lists every failed rule, not only
the first one.

The engine is pure: no Spring, no database, no clock. The service collects the facts
(`ApplicantFacts`, `UpgradeFacts`) and stores the result as a `decision_record` row
with inputs, outcome, reasons and `CreditPolicy.VERSION`.

## Consequences

- A reader sees every rule and number in one file.
- Tests are tables too: `CreditPolicyTest` has decision tables and exact boundaries;
  `CreditPolicyPropertiesTest` checks invariants over 5,000 generated inputs per flow.
- Rules are Java code, not data in a database. Changing a rule needs a deploy. A rule
  editor for business users is out of scope.
- The numbers are invented for a study project. They are not a real credit policy.
