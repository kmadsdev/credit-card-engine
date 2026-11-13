# ADR 0006: Optimistic locking and cooldown for upgrades

Status: accepted (2026-11-13)

## Context

A client can tap "upgrade" twice, or two channels can send the request at the same
time. Both requests may pass every rule. Only one upgrade may apply, and an account
must move exactly one variant up per approved request.

## Decision

- `card_account.version` is a JPA `@Version`. The upgrade flushes the account change
  before it issues the new card. If another transaction changed the account after
  this one read it, the update matches no row and Hibernate throws an optimistic lock
  error. The whole transaction rolls back and the API returns 409 `concurrent-update`.
- Upgrade state machine: `REQUESTED -> APPROVED -> APPLIED`. APPROVED (policy said yes)
  and APPLIED (row changed) are separate, so a lost race cannot look applied.
- Cooldown: 30 days (`CreditPolicy.UPGRADE_COOLDOWN`) from the last stored request of
  the account. A request inside the cooldown gets 429 with `Retry-After` and is not
  stored, so repeated tries do not extend the wait.
- No downgrade: the policy only proposes `current.next()`, and
  `CardAccount.upgradeTo` refuses any variant that is not higher.

I chose optimistic over pessimistic locking (`SELECT ... FOR UPDATE`) because conflicts
are rare and the flow calls an external port (the bureau) between read and write;
holding a row lock across that call would block other work on the account.

## Consequences

- `ConcurrentUpgradeIT` proves it deterministically: the test bureau holds both
  requests at a barrier after they read the account, so both read the same version.
  A second test fires 8 requests at once and checks that exactly one applies.
- A client that gets 409 may retry. It will then usually get 429 (cooldown).
