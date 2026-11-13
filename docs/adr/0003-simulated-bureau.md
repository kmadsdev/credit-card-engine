# ADR 0003: Simulated credit bureau behind a port

Status: accepted (2026-11-13)

## Context

Eligibility needs an external credit score. This project has no contract with any
bureau and must not pretend to have one.

## Decision

`application.port.CreditBureau` has one method: `int scoreFor(String cpf)`, 0 to 1000.
The only adapter, `SimulatedCreditBureau`, computes a deterministic number from a
SHA-256 hash of the CPF (range 250 to 949). Same CPF, same score. Tests replace it
with `TestCreditBureau`, where each test sets the score it needs.

The bureau is called only after validation passes, so an invalid CPF or a minor never
leaves the system.

## Where a real bureau plugs in

A new adapter implements `CreditBureau`, calls the provider over HTTP with a timeout,
a circuit breaker and a retry budget, and maps the provider's score to 0..1000. A
failure needs a decision of its own (reject, or park the application in a pending
state); this project does not model that state.

## Consequences

- No real bureau is integrated. The README says so.
- The score is not a credit model. It only gives the flows varied, repeatable input.
