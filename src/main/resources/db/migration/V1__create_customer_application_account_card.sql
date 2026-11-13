-- Customers, card applications (acquisition flow), card accounts and cards.
-- Money is numeric(14,2): whole centavos, never floating point.

create table customer (
    id                      uuid           primary key,
    cpf                     varchar(11)    not null,
    full_name               varchar(120)   not null,
    birth_date              date           not null,
    declared_monthly_income numeric(14, 2) not null check (declared_monthly_income >= 0),
    created_at              timestamptz    not null,
    updated_at              timestamptz    not null,
    constraint uk_customer_cpf unique (cpf)
);

create table card_application (
    id                      uuid           primary key,
    idempotency_key         varchar(80)    not null,
    request_hash            varchar(64)    not null,
    cpf                     varchar(11)    not null,
    full_name               varchar(120)   not null,
    birth_date              date           not null,
    declared_monthly_income numeric(14, 2) not null check (declared_monthly_income >= 0),
    status                  varchar(20)    not null,
    bureau_score            integer,
    approved_variant        varchar(20),
    approved_limit          numeric(14, 2),
    decision_id             uuid,
    customer_id             uuid references customer (id),
    account_id              uuid,
    card_id                 uuid,
    created_at              timestamptz    not null,
    updated_at              timestamptz    not null,
    version                 bigint         not null default 0,
    constraint uk_card_application_idempotency_key unique (idempotency_key)
);

create index ix_card_application_status_created on card_application (status, created_at desc);

create table card_account (
    id           uuid           primary key,
    customer_id  uuid           not null references customer (id),
    variant      varchar(20)    not null,
    credit_limit numeric(14, 2) not null check (credit_limit >= 0),
    status       varchar(20)    not null,
    opened_at    timestamptz    not null,
    updated_at   timestamptz    not null,
    -- Optimistic lock: two upgrades that read the same version cannot both commit.
    version      bigint         not null default 0,
    constraint uk_card_account_customer unique (customer_id),
    constraint ck_card_account_debit_limit check ((variant = 'DEBIT') = (credit_limit = 0))
);

-- Only a token and the last 4 digits. The full card number (PAN) is never stored.
create table card (
    id           uuid        primary key,
    account_id   uuid        not null references card_account (id),
    variant      varchar(20) not null,
    token        varchar(64) not null,
    last4        varchar(4)  not null,
    expiry_month integer     not null check (expiry_month between 1 and 12),
    expiry_year  integer     not null,
    status       varchar(20) not null,
    issued_at    timestamptz not null,
    constraint uk_card_token unique (token)
);

create index ix_card_account on card (account_id, issued_at desc);

alter table card_application
    add constraint fk_card_application_account foreign key (account_id) references card_account (id),
    add constraint fk_card_application_card foreign key (card_id) references card (id);
