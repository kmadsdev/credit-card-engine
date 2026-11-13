-- Simulated payment history (input of the upgrade flow), upgrade requests and the
-- decision record that keeps WHY each decision was made.

create table payment_history (
    id                bigint generated always as identity primary key,
    account_id        uuid           not null references card_account (id),
    reference_month   date           not null,
    statement_balance numeric(14, 2) not null check (statement_balance >= 0),
    paid_on_time      boolean        not null,
    constraint uk_payment_history_month unique (account_id, reference_month)
);

create table upgrade_request (
    id                      uuid           primary key,
    idempotency_key         varchar(80)    not null,
    request_hash            varchar(64)    not null,
    account_id              uuid           not null references card_account (id),
    status                  varchar(20)    not null,
    from_variant            varchar(20)    not null,
    from_limit              numeric(14, 2) not null,
    to_variant              varchar(20),
    to_limit                numeric(14, 2),
    declared_monthly_income numeric(14, 2) not null,
    bureau_score            integer        not null,
    decision_id             uuid           not null,
    created_at              timestamptz    not null,
    updated_at              timestamptz    not null,
    version                 bigint         not null default 0,
    constraint uk_upgrade_request_idempotency_key unique (idempotency_key)
);

create index ix_upgrade_request_account_created on upgrade_request (account_id, created_at desc);

-- Append-only audit of every policy decision: inputs, outcome, reasons, policy version.
create table decision_record (
    id             uuid        primary key,
    subject_type   varchar(20) not null,
    subject_id     uuid        not null,
    outcome        varchar(20) not null,
    policy_version varchar(20) not null,
    inputs         jsonb       not null,
    reasons        jsonb       not null,
    created_at     timestamptz not null
);

create index ix_decision_record_subject on decision_record (subject_type, subject_id);
