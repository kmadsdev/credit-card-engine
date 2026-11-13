-- Transactional outbox: events are written in the same transaction as the state
-- change, then a scheduled publisher sends them and sets published_at.

create table outbox_event (
    id             uuid         primary key,
    aggregate_type varchar(40)  not null,
    aggregate_id   uuid         not null,
    event_type     varchar(60)  not null,
    payload        jsonb        not null,
    created_at     timestamptz  not null,
    published_at   timestamptz,
    attempts       integer      not null default 0,
    last_error     varchar(500)
);

-- The publisher only reads unpublished rows, oldest first.
create index ix_outbox_event_pending on outbox_event (created_at) where published_at is null;
