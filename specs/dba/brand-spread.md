---
status: done
title: "Brand Default Spread Table"
requirement: "每個品牌可以設置點差，分為入金點差與出金點差；此表存放品牌的「預設點差」，套用於未加入任何點差群組的品牌幣種對。點差是百分比（%），以乘法套用在基礎匯率上，不是用加法的固定金額；點差不能超過 100%。"
---

# Brand Spread — DBA Spec

## Overview
`brand_spread` holds each brand's **default spread** (預設點差) — one deposit spread percentage (入金點差) and one withdrawal spread percentage (出金點差) per brand. Both are **percentages between 0 and 100 inclusive** (e.g. `0.5` means a 0.5% markup), applied **multiplicatively** to a base rate (`baseRate * (1 + spreadPercent / 100)`) — not a flat currency amount added to it. It is the fallback tier of the two-tier spread model: a brand currency pair that belongs to a [spread group](spread-group.md) uses that group's spread percentages; every pair that belongs to no group uses its brand's row here. Exactly one row per brand.

## Requirements
- One table: `brand_spread`.
- Exactly one row per brand — enforced by a unique constraint on `brand_id`.
- Both spread percentages are decimals with up to 8 decimal places, matching the precision style already used by `currency_pair.rate`, constrained to the range **0–100 inclusive** — a spread percentage cannot be negative or exceed 100%.
- Both spread percentages default to `0`, meaning "no markup applied" — a brand that has never been configured behaves as zero-spread rather than as missing data.
- Deleting a brand cascades to delete its spread row.
- Seeded with one zero-spread row for every brand that exists when the migration runs, so every brand has a resolvable default from day one.

## Implementation Details

### Table: `brand_spread`

| Column | Type | Constraint |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| brand_id | BIGINT | NOT NULL, UNIQUE, FK → `brand.id`, ON DELETE CASCADE |
| deposit_spread_percent | DECIMAL(18,8) | NOT NULL, DEFAULT 0, CHECK BETWEEN 0 AND 100 — percentage (入金點差百分比), applied as `baseRate * (1 + deposit_spread_percent / 100)` |
| withdrawal_spread_percent | DECIMAL(18,8) | NOT NULL, DEFAULT 0, CHECK BETWEEN 0 AND 100 — percentage (出金點差百分比), applied as `baseRate * (1 + withdrawal_spread_percent / 100)` |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

## Migration SQL — V006__create_brand_spread.sql

Comes after `V005__create_currency_pair.sql` (`specs/dba/currency-pair.md`) — the highest version applied so far. Must run after `V001__create_brand.sql` since this table FKs to `brand` and seeds from it.

```sql
CREATE TABLE brand_spread (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id BIGINT NOT NULL,
    deposit_spread DECIMAL(18,8) NOT NULL DEFAULT 0,
    withdrawal_spread DECIMAL(18,8) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_brand_spread_brand UNIQUE (brand_id),
    CONSTRAINT fk_brand_spread_brand FOREIGN KEY (brand_id) REFERENCES brand(id) ON DELETE CASCADE,
    CONSTRAINT ck_brand_spread_deposit CHECK (deposit_spread >= 0),
    CONSTRAINT ck_brand_spread_withdrawal CHECK (withdrawal_spread >= 0)
);

INSERT INTO brand_spread (brand_id, deposit_spread, withdrawal_spread)
SELECT b.id, 0, 0
FROM brand b
WHERE NOT EXISTS (SELECT 1 FROM brand_spread bs WHERE bs.brand_id = b.id);
```

The seed `INSERT ... WHERE NOT EXISTS` is idempotent — re-running it never duplicates a brand's row.

## Migration SQL — V015__rename_brand_spread_to_percent.sql (Delta: rename to percentage semantics, cap at 100%)

Comes after `V014__redesign_exchange_rate_per_brand.sql` (`specs/dba/exchange-rate.md`) — the highest version applied so far. Existing values are all `0` (no non-zero spread has been configured in any real usage yet), so a plain column rename carries the data forward correctly without any value transformation — `0` stays `0` under both the old "flat amount" and new "percentage" interpretation, and `0` is comfortably within the new `0–100` bound.

```sql
ALTER TABLE brand_spread
    DROP CHECK ck_brand_spread_deposit,
    DROP CHECK ck_brand_spread_withdrawal,
    CHANGE COLUMN deposit_spread deposit_spread_percent DECIMAL(18,8) NOT NULL DEFAULT 0,
    CHANGE COLUMN withdrawal_spread withdrawal_spread_percent DECIMAL(18,8) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_brand_spread_deposit_percent CHECK (deposit_spread_percent BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_brand_spread_withdrawal_percent CHECK (withdrawal_spread_percent BETWEEN 0 AND 100);
```

## Acceptance Criteria
- [x] `brand_spread` table exists with columns exactly as defined above.
- [x] Unique constraint on `brand_id`; a second row for the same brand is rejected.
- [x] `brand_id` foreign key to `brand.id` with `ON DELETE CASCADE`.
- [x] Both spread columns default to `0` and reject negative values (CHECK enforced).
- [x] One zero-spread row exists per seeded brand (7 rows) after the migration, and re-running the seed statement leaves the count unchanged.
- [x] After `V015` runs, `brand_spread` has columns `deposit_spread_percent`/`withdrawal_spread_percent` (not `deposit_spread`/`withdrawal_spread`) — same types/defaults, same CHECK behavior (now `BETWEEN 0 AND 100` instead of just `>= 0`) under their new constraint names.
- [x] A value greater than `100` (e.g. `100.00000001`) is rejected by the CHECK constraint, same as a negative value.
- [x] `100` itself is accepted (inclusive upper bound).
- [x] Existing rows' values are preserved unchanged by the rename (still `0.00000000`/`0.00000000` for every brand, since no non-zero spread had been configured).

---
## Execution Result
- Status: DONE
- Files changed: `specs/dba/brand-spread.md` (this file — `## Migration SQL` section already contained the final SQL; no code files elsewhere, per DBA convention no standalone `.sql` file was created).
- Applied against: MySQL 8.0.36 at `127.0.0.1:3306`, database `wdd`, user `app`. SQL was written to a scratch file (`.../scratchpad/v006_brand_spread.sql`, outside the repo) and executed with `mysql --default-character-set=utf8mb4 wdd < file.sql`.
- Verified:
  - `DESCRIBE brand_spread` and `SHOW CREATE TABLE brand_spread`: columns match the spec exactly — `id BIGINT PK AUTO_INCREMENT`, `brand_id BIGINT NOT NULL UNIQUE`, `deposit_spread DECIMAL(18,8) NOT NULL DEFAULT 0`, `withdrawal_spread DECIMAL(18,8) NOT NULL DEFAULT 0`, `created_at`/`updated_at TIMESTAMP` with the expected defaults; constraints present: `uk_brand_spread_brand` (UNIQUE on `brand_id`), `fk_brand_spread_brand` (FK → `brand.id` ON DELETE CASCADE), `ck_brand_spread_deposit` and `ck_brand_spread_withdrawal` (CHECK `>= 0`).
  - Seed: after migration, `SELECT COUNT(*) FROM brand_spread` = 7, one row per existing brand (brand_id 1–7), each with `deposit_spread = 0.00000000` and `withdrawal_spread = 0.00000000`.
  - Idempotency: re-ran the `INSERT ... WHERE NOT EXISTS` seed statement a second time; row count stayed at 7 (no duplicates).
  - UNIQUE constraint: `INSERT INTO brand_spread (brand_id, ...) VALUES (1, 0, 0)` (brand_id already present) failed with `ERROR 1062 Duplicate entry '1' for key 'brand_spread.uk_brand_spread_brand'`.
  - FK constraint: `INSERT INTO brand_spread (brand_id, ...) VALUES (999999, 0, 0)` failed with `ERROR 1452 Cannot add or update a child row: a foreign key constraint fails ... fk_brand_spread_brand`.
  - CHECK constraints: `deposit_spread = -1` failed with `ERROR 3819 Check constraint 'ck_brand_spread_deposit' is violated`; `withdrawal_spread = -0.00000001` failed with `ERROR 3819 Check constraint 'ck_brand_spread_withdrawal' is violated`.
  - Default value + CASCADE: inserted a temporary brand (`ZZTEST`), then `INSERT INTO brand_spread (brand_id) VALUES (@id)` (omitting the spread columns) produced a row with both spreads defaulted to `0.00000000`; deleting the temporary brand row from `brand` cascaded and removed the corresponding `brand_spread` row automatically. Final state confirmed back at exactly 7 brands / 7 `brand_spread` rows, one-to-one by `brand_id`.

---
## Execution Result — V015 Delta
- Status: DONE
- Files changed: `specs/dba/brand-spread.md` (this file — `## Migration SQL — V015__rename_brand_spread_to_percent.sql` section already contained the final SQL; no standalone `.sql` file created, per DBA convention).
- Applied against: MySQL 8.0.36 at `127.0.0.1:3306`, database `wdd`, user `app`. SQL was written to a scratch file (`.../scratchpad/v015_brand_spread.sql`, outside the repo) and executed with `mysql --default-character-set=utf8mb4 wdd < file.sql`.
- Pre-migration state confirmed: `brand_spread` was on the old `V006` shape — columns `deposit_spread`/`withdrawal_spread`, CHECK constraints `ck_brand_spread_deposit`/`ck_brand_spread_withdrawal` (`>= 0`), 7 rows all `0.00000000`/`0.00000000`.
- Migration executed cleanly (no errors).
- Verified post-migration:
  - `SHOW CREATE TABLE brand_spread`: columns renamed to `deposit_spread_percent`/`withdrawal_spread_percent`, both `DECIMAL(18,8) NOT NULL DEFAULT '0.00000000'`; old CHECK constraints dropped; new constraints `ck_brand_spread_deposit_percent` and `ck_brand_spread_withdrawal_percent` present with `BETWEEN 0 AND 100` semantics; `uk_brand_spread_brand` (UNIQUE) and `fk_brand_spread_brand` (FK → `brand.id` ON DELETE CASCADE) untouched.
  - Data preserved: all 7 rows (brand_id 1–7) still show `0.00000000`/`0.00000000` under the new column names, unchanged by the rename.
  - Upper bound rejected: `UPDATE brand_spread SET deposit_spread_percent = 100.00000001 WHERE brand_id = 2` failed with `ERROR 3819 (HY000): Check constraint 'ck_brand_spread_deposit_percent' is violated.`
  - Negative value still rejected: `UPDATE brand_spread SET withdrawal_spread_percent = -0.00000001 WHERE brand_id = 2` failed with `ERROR 3819 (HY000): Check constraint 'ck_brand_spread_withdrawal_percent' is violated.`
  - Inclusive upper bound accepted: `UPDATE brand_spread SET deposit_spread_percent = 100 WHERE brand_id = 2` succeeded, value stored as `100.00000000`.
  - Cleaned up: reset `brand_id = 2` back to `0.00000000`/`0.00000000` after the boundary tests, leaving live data exactly as it was before verification.
- All 8 Acceptance Criteria now checked; frontmatter `status` set to `done`.
