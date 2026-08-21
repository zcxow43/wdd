---
status: done
title: "Currency Master Table"
requirement: "新增幣種功能，要可以 CRUD（新增/查詢/修改/刪除），並加入一些預設幣種資料"
---

# Currency — DBA Spec

## Overview
`currency` is the master list of currencies the exchange rate center knows about (e.g. USD, JPY, TWD). Currencies are fully user-managed — created, edited, and deleted through the API — but the table is seeded with a small set of common default currencies so a fresh deployment isn't empty.

## Requirements
- One table: `currency`.
- Seeded with 5 default currencies on creation (`USD`, `JPY`, `TWD`, `EUR`, `CNY`) — see the seed migration below. These are ordinary rows: users can edit or delete them like any other currency through the API; they are not protected/special-cased.
- `code` is set once at creation and never changed afterward — only `name`, `symbol`, and `decimal_places` can be updated.

## Implementation Details

### Table: `currency`

| Column | Type | Constraint |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| code | VARCHAR(3) | NOT NULL, UNIQUE — 3 uppercase letters (e.g. `USD`, `JPY`, `TWD`) |
| name | VARCHAR(64) | NOT NULL |
| symbol | VARCHAR(8) | NOT NULL |
| decimal_places | TINYINT | NOT NULL, DEFAULT 2 — valid range 0–8 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

## Migration SQL — V002__create_currency.sql

Comes after `V001__create_brand.sql` (`specs/dba/brand.md`).

```sql
CREATE TABLE currency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(3) NOT NULL,
    name VARCHAR(64) NOT NULL,
    symbol VARCHAR(8) NOT NULL,
    decimal_places TINYINT NOT NULL DEFAULT 2,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_currency_code UNIQUE (code),
    CONSTRAINT ck_currency_decimal_places CHECK (decimal_places BETWEEN 0 AND 8)
);
```

## Migration SQL — V003__seed_default_currencies.sql (Delta: seed default currencies)

Comes after `V002__create_currency.sql` (this file, above). Uses `INSERT IGNORE` so re-running this migration never fails or duplicates if a default's `code` already exists (e.g. a user already created `USD` manually before this migration ran).

```sql
INSERT IGNORE INTO currency (code, name, symbol, decimal_places) VALUES
    ('USD', 'US Dollar', '$', 2),
    ('JPY', 'Japanese Yen', '¥', 0),
    ('TWD', 'Taiwan Dollar', 'NT$', 0),
    ('EUR', 'Euro', '€', 2),
    ('CNY', 'Chinese Yuan', '¥', 2);
```

## Acceptance Criteria
- [x] `currency` table exists with columns `id, code, name, symbol, decimal_places, created_at, updated_at` exactly as defined above.
- [x] `code` has a unique constraint.
- [x] `decimal_places` is constrained to the range 0–8.
- [x] After `V003` runs, `currency` contains at least the 5 default rows (`USD`, `JPY`, `TWD`, `EUR`, `CNY`) with the exact `name`/`symbol`/`decimal_places` values listed above.
- [x] Re-running `V003` against a database that already has one or more of these codes does not error and does not create duplicates.
- [x] The defaults are ordinary rows — editable and deletable through the Currency API like any user-created currency (no special-case column or guard added).

---
## Execution Result
- Status: DONE
- Files changed: `specs/dba/currency.md` (this spec, checked off acceptance criteria and status). No standalone `.sql` files created — migration SQL applied directly against the live MySQL database at `127.0.0.1:3306`, database `wdd`, via the `mysql` CLI.
- Notes: Ran pre-flight validation (env.md fields present, connection succeeded, database `wdd` already existed, target table `currency` did not exist). Executed `V002__create_currency.sql` from the Migration SQL section above directly against the live database: created the `currency` table with `id, code, name, symbol, decimal_places, created_at, updated_at`, unique constraint `uk_currency_code` on `code`, and check constraint `ck_currency_decimal_places` (0–8). Verified via `DESCRIBE currency`, `SHOW INDEX FROM currency`, and `SHOW CREATE TABLE currency`. Confirmed the CHECK constraint is actively enforced (an insert with `decimal_places = 9` was rejected with error 3819) and the UNIQUE constraint is enforced (a duplicate `code = 'USD'` insert was rejected with error 1062). Confirmed default `decimal_places = 2` applies when omitted. After testing, deleted the test row and confirmed `SELECT COUNT(*) FROM currency` returns 0 — table starts empty per spec, with no seed data inserted.

### Increment 1 — 2026-08-21
- Status: DONE
- Files changed: `specs/dba/currency.md` (this spec, checked off the new acceptance criteria and reset status to `done`). No standalone `.sql` files created — migration SQL applied directly against the live MySQL database at `127.0.0.1:3306`, database `wdd`, via the `mysql` CLI.
- Notes: Re-ran pre-flight validation (env.md fields present, connection to `127.0.0.1:3306` as `app` succeeded, database `wdd` exists). Confirmed `currency` table was empty (0 rows) prior to this increment, i.e. `V002` had already run but no seed data existed yet. Executed `V003__seed_default_currencies.sql` from the Migration SQL section above directly against the live database: `INSERT IGNORE` of the 5 default currencies (`USD`, `JPY`, `TWD`, `EUR`, `CNY`). Verified via `SELECT id, code, name, symbol, decimal_places FROM currency` that all 5 rows exist with the exact `name`/`symbol`/`decimal_places` values specified (USD/US Dollar/$/2, JPY/Japanese Yen/¥/0, TWD/Taiwan Dollar/NT$/0, EUR/Euro/€/2, CNY/Chinese Yuan/¥/2). Re-ran the same `INSERT IGNORE` statement a second time and confirmed no error was raised and the row count remained exactly 5 (idempotent). No special-case column, trigger, or guard was added to the schema, so the seeded rows remain ordinary, fully editable/deletable rows via the Currency API like any user-created currency.
