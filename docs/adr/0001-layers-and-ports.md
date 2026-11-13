# ADR 0001: Layers and ports

Status: accepted (2026-11-13)

## Context

The engine has two flows (acquisition, upgrade) and three outside systems: a credit
bureau, a card vault and a message broker. None of the three is real in this project.
I want the code that decides credit to be readable and testable without any of them.

## Decision

Packages, from the outside in:

| Package | Holds | May use |
|---|---|---|
| `api` | Controllers, request bodies, RFC 7807 errors | `application`, `domain` |
| `application` | Use cases, transactions, outbox, ports (`application.port`) | `domain`, `persistence` |
| `persistence` | JPA entities and Spring Data repositories | `domain` |
| `domain` | Policy engine, state machines, CPF, Luhn, money | JDK only |
| `adapter` | Implementations of the ports (all simulated) | `application.port`, `domain` |

`ArchitectureTest` (ArchUnit) fails the build when a dependency breaks this table.

I did not split JPA entities from domain objects. The entities call the domain state
machines for every status change, so the rules are still in one place, and I avoid a
mapping layer that would add code without adding a rule.

## Consequences

- The policy engine and the state machines run in plain unit tests (milliseconds).
- Replacing a simulated adapter with a real one touches one class and one bean.
- Entities carry JPA annotations; a move to another persistence technology would mean
  new entity classes. I accept that for a project of this size.
