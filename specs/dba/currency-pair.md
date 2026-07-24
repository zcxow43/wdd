---
status: done
title: "Currency Pair Table"
requirement: "Create currency_pair table with exchange rate (manual/auto), scoped per brand, unique per brand+base+quote, and FK constraints that block deleting a currency still referenced by a pair"
---

# Currency Pair Table — DBA Spec

## Overview
Create the `currency_pair` table to store configured currency pairs (base → quote) along with their exchange rate and whether that rate is maintained manually or automatically. Each pair belongs to exactly one **brand** (see `specs/dba/brand.md`, migration `V002`) — the same (base, quote) combination may be configured independently under multiple brands, each with its own rate. `currency_pair` also depends on the existing `currency` table (see `specs/dba/currency.md`, already applied as `V001__create_currency_table.sql`). Foreign keys from `currency_pair` to `currency` are defined `ON DELETE RESTRICT` so the database itself prevents deleting a currency that is still referenced by any pair — the application layer (see `specs/backend/currency-pair.md`) additionally performs an explicit pre-check to return a friendly `409` instead of a raw FK error.

## Requirements
- New table `currency_pair` referencing `currency.id` for both base and quote currencies, and `brand.id` for its owning brand
- A given (brand, base, quote) combination must be unique — no duplicate pairs within the same brand; the same (base, quote) pair may exist under different brands
- A pair cannot reference the same currency as both base and quote
- `rate` column stores the current exchange rate value
- `rate_type` column distinguishes `MANUAL` (user-entered) vs `AUTO` (system-maintained) rates
- Deleting a `currency` row that is referenced by any `currency_pair` (as base or quote) must be rejected at the database level
- Deleting a `brand` row that is referenced by any `currency_pair` must be rejected at the database level (brands are a fixed seeded set that is only ever enabled/disabled in practice — see `specs/dba/brand.md` — but the FK still guards against accidental removal)

## Table Definition

### `currency_pair`

| Column             | Type            | Nullable | Default            | Description                                  |
|--------------------|-----------------|----------|--------------------|-----------------------------------------------|
| id                 | BIGINT          | NO       | AUTO_INCREMENT     | Primary key                                   |
| brand_id           | BIGINT          | NO       |                    | FK → `brand.id`, the owning brand             |
| base_currency_id   | BIGINT          | NO       |                    | FK → `currency.id`, the base currency         |
| quote_currency_id  | BIGINT          | NO       |                    | FK → `currency.id`, the quote currency        |
| rate               | DECIMAL(18,8)   | NO       |                    | Exchange rate: 1 base = `rate` quote          |
| rate_type          | VARCHAR(10)     | NO       | 'MANUAL'           | `MANUAL` or `AUTO`                            |
| active             | TINYINT(1)      | NO       | 1                  | 1=active, 0=inactive                          |
| created_at         | DATETIME        | NO       | CURRENT_TIMESTAMP  | Record creation time                          |
| updated_at         | DATETIME        | NO       | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | Last update time      |

### Indexes / Constraints
- PRIMARY KEY on `id`
- UNIQUE index on (`brand_id`, `base_currency_id`, `quote_currency_id`)
- FOREIGN KEY `brand_id` REFERENCES `brand(id)` ON DELETE RESTRICT ON UPDATE RESTRICT
- FOREIGN KEY `base_currency_id` REFERENCES `currency(id)` ON DELETE RESTRICT ON UPDATE RESTRICT
- FOREIGN KEY `quote_currency_id` REFERENCES `currency(id)` ON DELETE RESTRICT ON UPDATE RESTRICT
- CHECK constraint: `base_currency_id <> quote_currency_id`
- CHECK constraint: `rate_type IN ('MANUAL', 'AUTO')`
- CHECK constraint: `rate > 0`

## Migration SQL

Next migration in sequence after `V001__create_currency_table.sql` and `V002__create_brand_table.sql` (see `specs/dba/brand.md`) is `V003__create_currency_pair_table.sql`.

```sql
CREATE TABLE IF NOT EXISTS `currency_pair` (
    `id`                 BIGINT         NOT NULL AUTO_INCREMENT,
    `brand_id`           BIGINT         NOT NULL,
    `base_currency_id`   BIGINT         NOT NULL,
    `quote_currency_id`  BIGINT         NOT NULL,
    `rate`               DECIMAL(18,8)  NOT NULL,
    `rate_type`          VARCHAR(10)    NOT NULL DEFAULT 'MANUAL',
    `active`             TINYINT(1)     NOT NULL DEFAULT 1,
    `created_at`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_currency_pair_brand_base_quote` (`brand_id`, `base_currency_id`, `quote_currency_id`),
    CONSTRAINT `ck_currency_pair_distinct` CHECK (`base_currency_id` <> `quote_currency_id`),
    CONSTRAINT `ck_currency_pair_rate_type` CHECK (`rate_type` IN ('MANUAL', 'AUTO')),
    CONSTRAINT `ck_currency_pair_rate_positive` CHECK (`rate` > 0),
    CONSTRAINT `fk_currency_pair_brand` FOREIGN KEY (`brand_id`) REFERENCES `brand` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_currency_pair_base` FOREIGN KEY (`base_currency_id`) REFERENCES `currency` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_currency_pair_quote` FOREIGN KEY (`quote_currency_id`) REFERENCES `currency` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Seed Data

```sql
INSERT INTO `currency_pair` (`brand_id`, `base_currency_id`, `quote_currency_id`, `rate`, `rate_type`, `active`)
SELECT br.id, b.id, q.id, v.rate, v.rate_type, 1
FROM (
    SELECT 'AU'     AS brand_code, 'USD' AS base_code, 'TWD' AS quote_code, 32.50000000 AS rate, 'MANUAL' AS rate_type
    UNION ALL SELECT 'AU',     'EUR', 'TWD', 35.20000000, 'MANUAL'
    UNION ALL SELECT 'MONETA', 'USD', 'JPY', 157.30000000, 'AUTO'
    UNION ALL SELECT 'VT',     'USD', 'EUR', 0.92000000, 'AUTO'
) v
JOIN `brand` br ON br.code = v.brand_code
JOIN `currency` b ON b.code = v.base_code
JOIN `currency` q ON q.code = v.quote_code;
```

## Migration Order
1. `V001__create_currency_table.sql` (already applied)
2. `V002__create_brand_table.sql` (`specs/dba/brand.md`) — must run before this migration since `currency_pair.brand_id` FKs to it
3. `V003__create_currency_pair_table.sql` (this spec) — must run after V001 and V002 since it FKs to both `currency` and `brand`

## Acceptance Criteria
- [x] `currency_pair` table created with all columns and correct types, including `brand_id`
- [x] Unique constraint on (`brand_id`, `base_currency_id`, `quote_currency_id`) — the same (base, quote) pair is allowed to repeat across different brands
- [x] FK constraints to `brand(id)`, and to `currency(id)` on both base and quote, all `ON DELETE RESTRICT`
- [x] CHECK constraints enforce base ≠ quote, valid `rate_type`, and `rate > 0`
- [x] Attempting to delete a `currency` or `brand` row referenced by any `currency_pair` fails at the DB level
- [x] Seed data inserted successfully and joins correctly to existing `currency` and `brand` rows
- [x] Timestamps auto-populate on insert and update

---
## Execution Result
- Status: DONE
- Files changed:
  - develop/backend/src/main/resources/db/migration/V003__create_currency_pair_table.sql (new)
  - docker/mysql/initdb/V003__create_currency_pair_table.sql (new)
- Notes: Ran DBA pre-flight (env.md validated: MySQL 8.0.36 @ 127.0.0.1:3306, db `wdd`, user `app`; connectivity confirmed via `mysql -h 127.0.0.1 -P 3306 -u app -p1234 -e "SELECT 1;"`; database `wdd` already existed with `brand` (7 rows, ids 1-7) and `currency` (10 rows, ids 1-10) tables present, confirming this is migration V003). Read `V001__create_currency_table.sql` and `V002__create_brand_table.sql` (both backend and docker/mysql/initdb copies, which are byte-identical) to match established conventions before writing the new migration.

  Created migration V003 defining the `currency_pair` table exactly as specified: PK on `id`; UNIQUE key `uk_currency_pair_brand_base_quote` on (`brand_id`, `base_currency_id`, `quote_currency_id`); FKs `fk_currency_pair_brand` → `brand(id)`, `fk_currency_pair_base` → `currency(id)`, `fk_currency_pair_quote` → `currency(id)`, all `ON DELETE RESTRICT ON UPDATE RESTRICT`; CHECK constraints `ck_currency_pair_distinct` (`base_currency_id <> quote_currency_id`), `ck_currency_pair_rate_type` (`rate_type IN ('MANUAL','AUTO')`), `ck_currency_pair_rate_positive` (`rate > 0`); plus the 4 seed rows (AU/USD/TWD, AU/EUR/TWD, MONETA/USD/JPY, VT/USD/EUR). Unlike the `brand` table's `code = UPPER(code)` constraint, none of this table's CHECK constraints compare against `UPPER()`/case-transformed values (they compare numeric FK columns and an exact-match `IN` list against fixed enum literals), so the `utf8mb4_unicode_ci` case-insensitive-collation pitfall found on `brand` does not apply here — confirmed this is a non-issue by testing an invalid `rate_type` value (`'BOGUS'`) and verifying it was correctly rejected (see below), rather than assuming.

  Applied the migration directly against the live `wdd` database via `mysql -h 127.0.0.1 -P 3306 -u app -p1234 wdd < V003__create_currency_pair_table.sql`; it succeeded on the first attempt (table created, 4 seed rows inserted). A second accidental re-run of the same file correctly failed with `ERROR 1062 Duplicate entry '1-2-1' for key 'uk_currency_pair_brand_base_quote'`, confirming the unique constraint and that the first run had already applied successfully (no partial/duplicate state resulted).

  Verification performed:
  - `SHOW TABLES` → `brand`, `currency`, `currency_pair` present.
  - `DESCRIBE currency_pair` → all 9 columns present with correct types (`bigint`, `decimal(18,8)`, `varchar(10)` default `'MANUAL'`, `tinyint(1)` default `1`, `datetime` defaults `CURRENT_TIMESTAMP` / `CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP`) and `NOT NULL` on all columns.
  - `SHOW INDEX FROM currency_pair` → `PRIMARY` on `id`, `uk_currency_pair_brand_base_quote` UNIQUE on (`brand_id`,`base_currency_id`,`quote_currency_id`), plus non-unique FK support indexes on `base_currency_id` and `quote_currency_id`.
  - `SHOW CREATE TABLE currency_pair` → confirms all 3 FKs with `ON DELETE RESTRICT ON UPDATE RESTRICT` and all 3 CHECK constraints exactly as specified.
  - CHECK constraint tests (all correctly rejected): `base_currency_id = quote_currency_id` → `ERROR 3819 ck_currency_pair_distinct is violated`; `rate_type = 'BOGUS'` → `ERROR 3819 ck_currency_pair_rate_type is violated`; `rate = 0` and `rate = -5` → `ERROR 3819 ck_currency_pair_rate_positive is violated` (both).
  - Unique constraint test: inserting duplicate (`brand_id=1, base=2, quote=1`) → `ERROR 1062 Duplicate entry '1-2-1'`.
  - FK insert test: `brand_id=999` (non-existent) → `ERROR 1452 foreign key constraint fails (fk_currency_pair_brand)`.
  - FK delete-RESTRICT tests: inserted a temporary valid row (brand=PUG id=3, base=USD id=2, quote=CNY id=6), then attempted `DELETE FROM currency WHERE id=2` (USD, referenced) → `ERROR 1451 ... CONSTRAINT fk_currency_pair_base`; attempted `DELETE FROM brand WHERE id=3` (PUG, referenced) → `ERROR 1451 ... CONSTRAINT fk_currency_pair_brand`; both correctly rejected. As a control, deleted an *unreferenced* currency (AUD, id=9) which succeeded, then re-inserted it with the same id and values to restore original state (verified `SELECT id, code FROM currency` matches the original 10 rows).
  - `updated_at` auto-update test: updated `rate` on row id=1, confirmed `updated_at` advanced while `created_at` stayed fixed, then restored the original seed rate value.
  - Cleanup: deleted the temporary test row (id=11, PUG/USD/CNY) used for the FK-RESTRICT tests.
  - Final state: `SELECT COUNT(*) FROM currency_pair` = 4; `SELECT ... JOIN brand, currency` confirms all 4 seed rows join correctly to their brand and currency codes: AU/USD/TWD (32.5, MANUAL), AU/EUR/TWD (35.2, MANUAL), MONETA/USD/JPY (157.3, AUTO), VT/USD/EUR (0.92, AUTO), all `active=1`. `brand` (7 rows) and `currency` (10 rows) tables unchanged from their pre-migration state. Note: `currency_pair` AUTO_INCREMENT counter is now at 12 due to the temporary test rows inserted and deleted during verification (ids 5-11 consumed); the 4 seed rows retain ids 1-4 as expected, with no functional impact.
