---
status: done
title: "Exchange Rate Table"
requirement: "同步外部匯率資料的畫面要顯示原始匯率、入金匯率、出金匯率三個欄位；入金/出金匯率要在同步當下就套用當時每個品牌生效的點差算出來存成快照（跟品牌幣種對頁面即時計算的版本是兩回事），一分鐘內不可再次同步。"
depends_on: []
---

# Exchange Rate — DBA Spec

## Overview
`exchange_rate` stores, for every (global [currency pair definition](currency-pair-definition.md), brand) combination, a per-minute snapshot of three values: the plain market rate synced from the external provider (原始匯率), and that brand's deposit/withdrawal rate (入金匯率／出金匯率) computed **at that exact sync moment** by adding the brand's then-currently-effective spread ([brand-spread.md](brand-spread.md)'s 品牌預設點差 or [spread-group.md](spread-group.md)'s 點差群組, whichever currently applies — the same resolution [currency-pair.md](currency-pair.md)'s `GET /api/currency-pairs` already uses for its own live-computed `depositRate`/`withdrawalRate`). This is a **historical snapshot, not a live view**: once written, a row's `deposit_rate`/`withdrawal_rate` never change even if the brand's spread configuration changes afterward — that live-recomputed-on-every-read version already exists on `currency_pair` (see [currency-pair.md](currency-pair.md)); this table is the frozen record of what applied the moment each sync ran.

## Requirements
- One table: `exchange_rate`.
- One row per `(currency_pair_definition, brand, rate_minute)` — every sync fans out across every existing brand, so a single sync produces up to (definition count × brand count) rows.
- The natural key is `(currency_pair_definition_id, brand_id, rate_minute)`: `rate_minute` is the sync time with seconds/sub-second components zeroed (truncated to the minute), so re-syncing within the same minute updates that same row instead of creating a new one — enforced by a unique constraint. (The application layer additionally rejects a sync attempted within 60 real-world seconds of the last successful one, regardless of minute boundaries — see [exchange-rate.md](../backend/exchange-rate.md); this table has no column dedicated to that check, it reads `updated_at` instead.)
- `rate` is the plain value returned by the external provider for that pair, unmodified — no spread, markup, or adjustment. Must be positive.
- `deposit_rate` = `rate` plus that brand's effective deposit spread at sync time; `withdrawal_rate` = `rate` plus that brand's effective withdrawal spread at sync time. Both computed once, at insert/update time, and never recalculated afterward. Both must be positive.
- Deleting a `currency_pair_definition` cascades to delete its synced rate history (it has no meaning without the definition it prices).
- No seed data — the table starts empty and is populated only by the sync action (see [exchange-rate.md](../backend/exchange-rate.md)).

## Implementation Details

### Table: `exchange_rate`

| Column | Type | Constraint |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| currency_pair_definition_id | BIGINT | NOT NULL, FK → `currency_pair_definition.id`, ON DELETE CASCADE |
| brand_id | BIGINT | NOT NULL, FK → `brand.id` |
| rate | DECIMAL(18,8) | NOT NULL, CHECK > 0 — the plain rate returned by the external provider, no spread applied (原始匯率) |
| deposit_rate | DECIMAL(18,8) | NOT NULL, CHECK > 0 — `rate` plus this brand's effective deposit spread at sync time (入金匯率) |
| withdrawal_rate | DECIMAL(18,8) | NOT NULL, CHECK > 0 — `rate` plus this brand's effective withdrawal spread at sync time (出金匯率) |
| rate_minute | DATETIME | NOT NULL — sync time truncated to the minute (seconds always `:00`) |
| source | VARCHAR(50) | NOT NULL — identifies the external provider (e.g. `open.er-api.com`) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

Unique constraint: `uk_exchange_rate_definition_brand_minute` on `(currency_pair_definition_id, brand_id, rate_minute)`. This same composite index also serves "give me the latest rate for this definition+brand" lookups (`WHERE currency_pair_definition_id = ? AND brand_id = ? ORDER BY rate_minute DESC LIMIT 1`), so no separate index is needed. `MAX(updated_at)` across the whole table (all definitions, all brands) is what the backend's 60-second cooldown check reads — a fully failed sync (nothing upserted anywhere) leaves `updated_at` untouched, so it does not itself trigger a cooldown.

## Migration SQL — V013__create_exchange_rate.sql

Comes after `V012__seed_usd_brand_currency_pairs.sql` (`specs/dba/currency-pair.md`) — the highest version applied so far. Must run after `V004__create_currency_pair_definition.sql` since this table FKs to it.

```sql
CREATE TABLE exchange_rate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    currency_pair_definition_id BIGINT NOT NULL,
    raw_rate DECIMAL(18,8) NOT NULL,
    spread_cents INT NOT NULL DEFAULT 0,
    rate DECIMAL(18,8) NOT NULL,
    rate_minute DATETIME NOT NULL,
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_exchange_rate_definition_minute UNIQUE (currency_pair_definition_id, rate_minute),
    CONSTRAINT ck_exchange_rate_raw_positive CHECK (raw_rate > 0),
    CONSTRAINT ck_exchange_rate_spread_non_negative CHECK (spread_cents >= 0),
    CONSTRAINT ck_exchange_rate_positive CHECK (rate > 0),
    CONSTRAINT fk_exchange_rate_definition FOREIGN KEY (currency_pair_definition_id) REFERENCES currency_pair_definition(id) ON DELETE CASCADE
);
```

## Migration SQL — V014__redesign_exchange_rate_per_brand.sql (Delta: brand-scoped snapshot with deposit/withdrawal rates)

Comes after `V013__create_exchange_rate.sql` (this file, above) — the highest version applied so far. This spec's `V014` was previously written to just drop the mistaken `raw_rate`/`spread_cents` split, but that migration was never actually applied to any live database (confirmed: the live `wdd` database is still on the exact `V013` shape) — so rather than apply that now-superseded intermediate step and immediately follow it with another migration, this `V014` is rewritten in place to go directly from `V013`'s live shape to the final one below in a single migration. Existing rows are test data from manual verification (not real seed data, matching this table's "no seed data" contract), so they're simply cleared rather than migrated column-by-column.

```sql
DELETE FROM exchange_rate;

ALTER TABLE exchange_rate
    DROP CHECK ck_exchange_rate_raw_positive,
    DROP CHECK ck_exchange_rate_spread_non_negative,
    DROP CHECK ck_exchange_rate_positive,
    DROP INDEX uk_exchange_rate_definition_minute,
    DROP COLUMN raw_rate,
    DROP COLUMN spread_cents,
    DROP COLUMN rate,
    ADD COLUMN brand_id BIGINT NOT NULL AFTER currency_pair_definition_id,
    ADD COLUMN rate DECIMAL(18,8) NOT NULL AFTER brand_id,
    ADD COLUMN deposit_rate DECIMAL(18,8) NOT NULL AFTER rate,
    ADD COLUMN withdrawal_rate DECIMAL(18,8) NOT NULL AFTER deposit_rate,
    ADD CONSTRAINT uk_exchange_rate_definition_brand_minute UNIQUE (currency_pair_definition_id, brand_id, rate_minute),
    ADD CONSTRAINT fk_exchange_rate_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
    ADD CONSTRAINT ck_exchange_rate_rate_positive CHECK (rate > 0),
    ADD CONSTRAINT ck_exchange_rate_deposit_positive CHECK (deposit_rate > 0),
    ADD CONSTRAINT ck_exchange_rate_withdrawal_positive CHECK (withdrawal_rate > 0);
```

## Acceptance Criteria
- [x] `exchange_rate` table exists with columns exactly as defined in `### Table: exchange_rate` above — `id`, `currency_pair_definition_id`, `brand_id`, `rate`, `deposit_rate`, `withdrawal_rate`, `rate_minute`, `source`, `created_at`, `updated_at`; no `raw_rate`/`spread_cents` column exists.
- [x] Unique constraint `uk_exchange_rate_definition_brand_minute` on `(currency_pair_definition_id, brand_id, rate_minute)` — a second insert for the same definition, brand, and truncated minute is rejected (the application performs an upsert instead, per the backend spec).
- [x] `currency_pair_definition_id` foreign key has `ON DELETE CASCADE` — deleting a definition removes its synced rate history across every brand.
- [x] `brand_id` foreign key references `brand.id`.
- [x] `rate`/`deposit_rate`/`withdrawal_rate` CHECK constraints each reject zero/negative values.
- [x] Table starts empty (no seed rows) — true of a fresh deployment; the migration itself clears any pre-existing test rows from the prior schema shape.

---
## Execution Result
- Status: DONE
- Files changed: `specs/dba/exchange-rate.md` (SQL applied live; no standalone `.sql` files created)
- Notes:
  - Pre-flight passed: connected to MySQL 8.0.36 at `127.0.0.1:3306` as `app`, database `wdd` already existed.
  - Confirmed live `exchange_rate` was still on the old `V013` shape (`raw_rate`, `spread_cents`, no `brand_id`/`deposit_rate`/`withdrawal_rate`), with 63 pre-existing test rows, matching the note in this spec's `V014` section.
  - Executed the rewritten `V014__redesign_exchange_rate_per_brand.sql` block verbatim against the live `wdd` database via the `mysql` CLI: cleared the 63 old test rows, dropped the old CHECK/unique constraints and `raw_rate`/`spread_cents`/`rate` columns, then added `brand_id`, `rate`, `deposit_rate`, `withdrawal_rate`, the new `uk_exchange_rate_definition_brand_minute` unique key, `fk_exchange_rate_brand` FK to `brand.id`, and the three positive-value CHECK constraints. `SHOW CREATE TABLE exchange_rate` confirms the resulting shape matches `### Table: exchange_rate` exactly (no `raw_rate`/`spread_cents` remain).
  - Verified behavior with live inserts/deletes (all test rows cleaned up afterward, leaving the table empty and `currency_pair_definition` back at its original 9 rows):
    - Valid insert (with `brand_id`, `rate`, `deposit_rate`, `withdrawal_rate`) succeeds; a second insert with the same `(currency_pair_definition_id, brand_id, rate_minute)` fails with `ERROR 1062 Duplicate entry ... uk_exchange_rate_definition_brand_minute`.
    - `rate <= 0` fails `ck_exchange_rate_rate_positive`; `deposit_rate <= 0` fails `ck_exchange_rate_deposit_positive`; `withdrawal_rate <= 0` fails `ck_exchange_rate_withdrawal_positive`.
    - Insert referencing a non-existent `brand_id` fails FK constraint `fk_exchange_rate_brand`.
    - Created a temporary `currency_pair_definition` (AUD→CNY), inserted a referencing `exchange_rate` row, deleted the definition, and confirmed the `exchange_rate` row was cascade-deleted, then removed the temporary definition itself.
  - Final state: `exchange_rate` has 0 rows (no seed data, per spec); `currency_pair_definition` unchanged at 9 rows.
  - All Acceptance Criteria checked off above.
