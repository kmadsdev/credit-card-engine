# ADR 0007: Idempotency keys, and money as BigDecimal

Status: accepted (2026-11-13)

## Idempotency

`POST /applications` and `POST /accounts/{id}/upgrade-requests` require an
`Idempotency-Key` header (8 to 80 characters of `[A-Za-z0-9_-]`).

- The key is stored with the resource (unique index) together with a SHA-256 hash of
  the request fields.
- Same key, same body: the stored resource comes back with 200 and
  `Idempotent-Replayed: true`. No second decision is made.
- Same key, other body: 422 `idempotency-key-reused`.
- Two requests with the same key at the same time: one wins; the other gets the
  winner as a replay. For an application the loser fails on the unique index of the
  key. For an upgrade it usually fails first on the account version check; the service
  then looks the key up before it answers 409 (`ConcurrentUpgradeIT` covers it).

Keys do not expire in this project. A real system would expire them (for example after
24 hours) and clean the column.

## Money

- All amounts are `BigDecimal` with scale 2 in Java and `numeric(14,2)` in PostgreSQL.
- `Money.of` refuses negative values and fractions of a centavo instead of rounding,
  so input cannot slide across a variant boundary.
- Variant bands use inclusive centavo bounds: BASE is 0.01 to 2000.00, PLATINUM is
  2000.01 to 8000.00, BLACK is 8000.01 and up. Tests check 2000.00, 2000.01,
  8000.00 and 8000.01 exactly.
- Computed limits are rounded down to R$ 10 and capped at R$ 30,000.00.
