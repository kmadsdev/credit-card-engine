# ADR 0005: Transactional outbox, broker behind a port

Status: accepted (2026-11-13)

## Context

Other systems need to know when an application is decided, an account opens, a card
is issued or an account is upgraded. Writing to the database and then sending to a
broker in two steps can lose an event (crash after commit) or send an event for a
change that rolled back.

## Decision

- Each use case writes its events to `outbox_event` in the same transaction as the
  state change. `Outbox.append` requires an open transaction (`Propagation.MANDATORY`).
- `OutboxPublisher` runs on a schedule (`app.outbox.publish-interval`, default 2 s),
  locks a batch with `FOR UPDATE SKIP LOCKED`, sends each event through the
  `EventPublisher` port and sets `published_at`. A failed event stays pending, its
  `attempts` and `last_error` are updated, and the rest of the batch continues.
- Delivery is at least once. Consumers deduplicate by event id.
- The adapter in this repository, `LogEventPublisher`, writes each event as one JSON
  log line. No message broker is used or included.

## Where a real broker plugs in

A new adapter implements `EventPublisher.publish(Event)`: it sends the event with the
aggregate id as the message key (to keep per-aggregate order) and waits for the
broker's acknowledgement before returning. That is the only change. Retention and
cleanup of published rows would also be needed; they are not built.

## Consequences

- Events never disagree with the database.
- Metric `cards_outbox_pending` shows the backlog; `cards_outbox_failures_total` counts errors.
- There is no dead-letter handling after N attempts. That is listed as not built.
