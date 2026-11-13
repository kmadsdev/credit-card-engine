# ADR 0004: The full card number never enters the engine

Status: accepted (2026-11-13)

## Context

A full card number (PAN) in a database, a log line or an event puts every system that
touches it in PCI DSS scope. The engine only needs to know that a card exists and to
show its last 4 digits.

## Decision

- `application.port.CardVault.issueNumber()` returns a token and the last 4 digits.
  The vault creates the number; the engine never receives it.
- The `card` table has `token` and `last4` columns, and no column for a number.
- `SimulatedCardVault` generates a 16-digit Luhn-valid number with the test prefix
  `990000`, returns a random `tok_` + 32 hex token and the last 4 digits, and drops
  the number. A token here cannot be turned back into a number.
- API responses show `**** **** **** 1234`.
- The log encoder masks any 13 to 19 digit sequence as a second line of defense.

## Consequences

- An integration test reads every column of the stored card and checks that none
  holds 13 to 19 consecutive digits.
- There is no detokenization and no card authorization in this project.
- A real vault is a separate, hardened service with its own key management. Out of scope.
