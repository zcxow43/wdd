---
status: done
title: "Currency Table"
requirement: "Create currency table with seed data for the currency API. Delta: currency has no enable/disable concept — the active column is dropped; currencies are always usable once created."
---

# Currency Table — DBA Spec

## Overview
Create the currency reference table to store currency codes, names, and metadata. Populate with seed data for common currencies. **Current state: `currency` has no `active`/enable-disable column** — every currency row is always usable; there is no soft-delete or enable/disable concept for this entity (unlike `brand`/`currency_pair`, which do have one).

## Requirements
- Single table `currency` to hold all supported currencies
- Seed with at least 10 common currencies including TWD, USD, EUR, JPY, GBP, CNY, HKD, SGD, AUD, CAD
- No enable/disable flag — a currency is either present (usable) or deleted (via the existing in-use guard, `specs/backend/currency.md`); there is no intermediate "disabled but still present" state

## Table Definition

### `currency`

| Column       | Type           | Nullable | Default            | Description              |
|--------------|----------------|----------|--------------------|--------------------------|
| id           | BIGINT         | NO       | AUTO_INCREMENT     | Primary key              |
| code         | VARCHAR(3)     | NO       |                    | ISO 4217 currency code   |
| name         | VARCHAR(100)   | NO       |                    | Currency English name     |
| name_zh      | VARCHAR(100)   | YES      |                    | Currency Chinese name     |
| symbol       | VARCHAR(10)    | YES      |                    | Currency symbol (e.g. $)  |
| decimal_places | INT          | NO       | 2                  | Number of decimal places |
| created_at   | DATETIME       | NO       | CURRENT_TIMESTAMP  | Record creation time     |
| updated_at   | DATETIME       | NO       | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | Last update time |

### Indexes
- PRIMARY KEY on `id`
- UNIQUE index on `code`

## Migration SQL

```sql
CREATE TABLE IF NOT EXISTS `currency` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `code`           VARCHAR(3)    NOT NULL,
    `name`           VARCHAR(100)  NOT NULL,
    `name_zh`        VARCHAR(100)  NULL,
    `symbol`         VARCHAR(10)   NULL,
    `decimal_places`  INT          NOT NULL DEFAULT 2,
    `active`         TINYINT(1)    NOT NULL DEFAULT 1,
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_currency_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Seed Data

```sql
INSERT INTO `currency` (`code`, `name`, `name_zh`, `symbol`, `decimal_places`, `active`) VALUES
('TWD', 'New Taiwan Dollar',       '新台幣',     'NT$', 0, 1),
('USD', 'United States Dollar',    '美元',       '$',   2, 1),
('EUR', 'Euro',                    '歐元',       '€',   2, 1),
('JPY', 'Japanese Yen',            '日圓',       '¥',   0, 1),
('GBP', 'British Pound Sterling',  '英鎊',       '£',   2, 1),
('CNY', 'Chinese Yuan',            '人民幣',     '¥',   2, 1),
('HKD', 'Hong Kong Dollar',        '港幣',       'HK$', 2, 1),
('SGD', 'Singapore Dollar',        '新加坡幣',   'S$',  2, 1),
('AUD', 'Australian Dollar',       '澳幣',       'A$',  2, 1),
('CAD', 'Canadian Dollar',         '加幣',       'C$',  2, 1);
```

## Migration SQL — V010 (Delta: drop the `active` column)

Next migration in sequence after `V009__create_currency_pair_definition_table.sql` (`specs/dba/currency-pair-definition.md`) is `V010__drop_currency_active_column.sql`.

```sql
-- V010__drop_currency_active_column.sql
-- Drops currency.active: currency has no enable/disable concept — every row
-- is always usable once created (delete via the existing in-use guard
-- instead, specs/backend/currency.md). Purely additive elsewhere — no
-- other table is touched.
-- Rollback: ALTER TABLE `currency` ADD COLUMN `active` TINYINT(1) NOT NULL DEFAULT 1;
--           (rolled-back rows would all read as 1/active, indistinguishable from before)

ALTER TABLE `currency` DROP COLUMN `active`;
```

Apply to both `develop/backend/src/main/resources/db/migration/` and `docker/mysql/initdb/`, matching the existing dual-location convention for every prior migration.

## Migration Order
1. `V001`–`V009` (already applied)
2. `V010__drop_currency_active_column.sql` (this delta)

## Acceptance Criteria
- [x] `currency` table created with all columns and correct types
- [x] Unique constraint on `code` column
- [x] 10 seed records inserted successfully
- [x] `active` defaults to 1, `decimal_places` defaults to 2 (historical — describes the table before this delta; see below)
- [x] Timestamps auto-populate on insert and update

### Delta: drop the `active` column
- [x] `currency.active` column no longer exists — confirmed via `DESCRIBE currency`
- [x] Existing seed rows are unaffected by the drop (all other columns/values unchanged) — confirmed via `SELECT * FROM currency`
- [x] No other table (`brand`, `currency_pair`, `currency_pair_definition`, `spread_*`) is altered by this migration
- [x] `V010` applied identically to `develop/backend/src/main/resources/db/migration/` and `docker/mysql/initdb/`

---
## Execution Result
- Status: DONE
- Files changed:
  - develop/backend/src/main/resources/db/migration/V001__create_currency_table.sql (new)
  - docker/mysql/initdb/V001__create_currency_table.sql (new)
  - docker/docker-compose.yml (mounted ./mysql/initdb to /docker-entrypoint-initdb.d so the mysql container auto-runs init scripts on first boot)
- Notes: Ran DBA pre-flight (env.md validated, connectivity to 127.0.0.1:3306 confirmed, database `wdd` already existed). Created migration V001 defining the `currency` table (PK on id, UNIQUE key on code, defaults for decimal_places=2 and active=1, created_at/updated_at auto-managed) plus the 10 seed rows (TWD, USD, EUR, JPY, GBP, CNY, HKD, SGD, AUD, CAD). Applied the migration directly against the live `wdd` database via `mysql ... < V001__create_currency_table.sql` and verified with `SHOW TABLES`, `DESCRIBE currency`, `SHOW INDEX FROM currency`, and `SELECT COUNT(*) FROM currency` — table exists, PRIMARY and uk_currency_code indexes present, 10 rows inserted. No pre-existing migration files were present, so this is V001.

### Increment 2 — 2026-07-31
- Status: DONE
- Files changed:
  - develop/backend/src/main/resources/db/migration/V010__drop_currency_active_column.sql (new)
  - docker/mysql/initdb/V010__drop_currency_active_column.sql (new, identical copy)
- Notes: Executed only the "Delta: drop the `active` column" section per instructions; V001–V009 and seed data were left untouched. Ran DBA pre-flight (env.md validated with all required fields; `mysql -h 127.0.0.1 -P 3306 -u app -p1234 -e "SELECT 1;"` succeeded; database `wdd` already existed with V001–V009 already applied, so no CREATE DATABASE step was needed). Confirmed pre-migration state via `DESCRIBE currency` showed the `active tinyint(1) NOT NULL DEFAULT 1` column present. Created `V010__drop_currency_active_column.sql` (`ALTER TABLE currency DROP COLUMN active;`) in both `develop/backend/src/main/resources/db/migration/` and `docker/mysql/initdb/`, diffed the two copies to confirm byte-identical content. Applied the migration directly to the live `wdd` database via `mysql ... < V010__drop_currency_active_column.sql` with no errors. Verified: `DESCRIBE currency` no longer lists `active` (columns now id, code, name, name_zh, symbol, decimal_places, created_at, updated_at); `SELECT * FROM currency` returns all 10 original seed rows unchanged in every remaining column; `SHOW TABLES` confirms `audit_request`, `brand`, `currency_pair`, `currency_pair_definition`, `spread_default`, `spread_group`, `spread_group_member` are all still present and were not touched by this single-statement, single-table migration. `V010` is confirmed identical in both required directories.
