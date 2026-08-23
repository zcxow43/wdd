---
status: done
title: "Currency Pair Definition Table"
requirement: "新增幣種對功能：全系統共用的幣種對定義（基準幣/報價幣/精度），無開啟關閉，刪除前需所有品牌幣種對皆已關閉"
---

# Currency Pair Definition — DBA Spec

## Overview
`currency_pair_definition` is the global, brand-agnostic definition of a currency pair (e.g. USD/JPY) — which two currencies it pairs and at what decimal precision exchange rates for it are stored. It has no `active` column; enable/disable happens per brand on `currency_pair` (see [currency-pair.md](currency-pair.md)), which FKs to this table.

## Requirements
- One table: `currency_pair_definition`.
- `base_currency_id` and `quote_currency_id` must reference two different currencies, and the `(base, quote)` combination must be unique.
- No `active` column — this table has no enable/disable state at all.
- `precision` is the decimal-place count every `currency_pair` row under this definition must respect for its `rate` (enforced at the application layer in [currency-pair.md](currency-pair.md), not here, since a single `DECIMAL` column can't have a different scale per row).

## Implementation Details

### Table: `currency_pair_definition`

| Column | Type | Constraint |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| base_currency_id | BIGINT | NOT NULL, FK → `currency.id` |
| quote_currency_id | BIGINT | NOT NULL, FK → `currency.id` |
| precision | TINYINT | NOT NULL, DEFAULT 4 — valid range 0–8 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

## Migration SQL — V004__create_currency_pair_definition.sql

Comes after `V003__seed_default_currencies.sql` (`specs/dba/currency.md`).

```sql
CREATE TABLE currency_pair_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_currency_id BIGINT NOT NULL,
    quote_currency_id BIGINT NOT NULL,
    `precision` TINYINT NOT NULL DEFAULT 4,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_currency_pair_definition UNIQUE (base_currency_id, quote_currency_id),
    CONSTRAINT ck_currency_pair_definition_diff CHECK (base_currency_id <> quote_currency_id),
    CONSTRAINT ck_currency_pair_definition_precision CHECK (`precision` BETWEEN 0 AND 8),
    CONSTRAINT fk_currency_pair_definition_base FOREIGN KEY (base_currency_id) REFERENCES currency(id),
    CONSTRAINT fk_currency_pair_definition_quote FOREIGN KEY (quote_currency_id) REFERENCES currency(id)
);
```

Note: `precision` is a reserved word in MySQL 8.0 and must be backtick-quoted in both the column definition and the CHECK constraint referencing it, or the statement fails with a syntax error.

## Acceptance Criteria
- [x] `currency_pair_definition` table exists with columns exactly as defined above.
- [x] Unique constraint on `(base_currency_id, quote_currency_id)`.
- [x] CHECK constraint rejects `base_currency_id = quote_currency_id`.
- [x] `precision` is constrained to the range 0–8.
- [x] `base_currency_id`/`quote_currency_id` are foreign keys to `currency.id`.

---
## Execution Result
- Status: DONE
- Files changed: `specs/dba/currency-pair-definition.md` (SQL executed against live DB; column `precision` backtick-quoted since it is a MySQL 8.0 reserved word)
- Notes: Pre-flight passed (env.md validated, connection to `127.0.0.1:3306` as `app` succeeded, database `wdd` already existed). Ran `V004__create_currency_pair_definition.sql` directly via `mysql` CLI against `wdd`. `SHOW CREATE TABLE currency_pair_definition` confirms all columns, the unique key, both CHECK constraints, and both FKs to `currency(id)` match the spec exactly. Functionally verified: (1) valid insert (USD/JPY, precision 4) succeeded; (2) same-currency insert rejected by `ck_currency_pair_definition_diff`; (3) duplicate `(base, quote)` rejected by `uk_currency_pair_definition`; (4) precision 9 rejected by `ck_currency_pair_definition_precision`; (5) nonexistent currency ids rejected by FK constraint. Test row cleaned up afterward — table is empty (0 rows) as expected since this spec seeds no data.

### Increment 2 — 2026-08-22
- Trigger: Live `wdd` database was reset; `brand` (V001) and `currency` (V002/V003) had already been re-applied with seed data, but `currency_pair_definition` did not yet exist. Re-ran this spec's migration to restore the table, per explicit instruction to re-apply every DBA spec regardless of `done` status.
- Pre-flight: `env.md` validated (Engine MySQL 8.0.36, Host `127.0.0.1:3306`, Database `wdd`, User `app`). `mysql -h 127.0.0.1 -P 3306 -u app -p1234 -e "SELECT 1;"` succeeded. `SHOW DATABASES LIKE 'wdd';` confirmed the database already existed (no CREATE DATABASE needed).
- Pre-check: `SHOW TABLES` in `wdd` returned only `brand` and `currency` (5 seed rows in `currency`) — confirmed `currency_pair_definition` was indeed missing before this run.
- Applied `V004__create_currency_pair_definition.sql` (verbatim SQL from the `## Migration SQL` section above, unchanged) by writing it to a UTF-8 scratch file and piping it in via `mysql --default-character-set=utf8mb4 wdd < file.sql` (no non-ASCII characters in this file, but used the safe path per instructions) — no errors.
- Verification: `SHOW CREATE TABLE currency_pair_definition` confirmed all 6 columns, `PRIMARY KEY (id)`, `UNIQUE KEY uk_currency_pair_definition (base_currency_id, quote_currency_id)`, `CHECK ck_currency_pair_definition_diff`, `CHECK ck_currency_pair_definition_precision`, and FKs `fk_currency_pair_definition_base`/`fk_currency_pair_definition_quote` → `currency(id)` — exact match to spec. Re-ran the same functional constraint tests as Increment 1 inside a transaction (valid insert; same-currency reject; precision-9 reject; nonexistent-FK reject; duplicate-pair reject), all behaved as expected, then rolled back / deleted the leftover test row. Final state: `currency_pair_definition` exists with 0 rows (seeds no data by design).
- Files changed: none (only this spec file's `## Execution Result` section was appended to; the live database schema is the artifact of this run).
