---
status: done
title: "Spread (點差) Tables"
requirement: "每個品牌幣種對可以配置點差, 點差分為預設點差或客制點差, 有入金出金兩個欄位; 客制點差可將多個幣種對加入同一組, 每個幣種對最多屬於一組客制點差; 未配置的幣種對使用該品牌的預設點差; 點差依品牌區分"
---

# Spread (點差) Tables — DBA Spec

## Overview
Adds the storage for the brand-scoped spread ("點差") feature: every brand has exactly one **default spread** (預設點差, one row per brand, seeded), and any number of **custom spread groups** (客制點差群組) it can freely create/update/delete. Each currency pair can be assigned into **at most one** custom spread group; pairs not assigned to any group fall back to their brand's default spread. This is intentionally **separate from the existing `brand` table** (`specs/dba/brand.md`), which is a closed, fixed-set feature ("no create/delete, only `active` toggle") — spread defaults are modeled as their own 1:1-per-brand table so that spec's contract is not touched.

Depends on `brand` (`specs/dba/brand.md`) and `currency_pair` (`specs/dba/currency-pair.md`), both already migrated.

## Requirements
- One `spread_default` row per brand (1:1), holding `deposit_spread` (入金) and `withdraw_spread` (出金); seeded with one zero-value row per existing seeded brand.
- `spread_group`: brand-scoped, freely CRUD-able, holds a name plus `deposit_spread` and `withdraw_spread`.
- `spread_group_member`: join table assigning `currency_pair` rows into a `spread_group`. A `currency_pair_id` may appear in **at most one** membership row at the database level (enforced via a `UNIQUE` key), guaranteeing "品牌幣種對最多被加入到一組點差中".
- Deleting a `spread_group` removes its memberships (pairs revert to using the default spread); deleting a `currency_pair` removes any membership row for it.

## Table Definitions

### `spread_default`

| Column          | Type          | Nullable | Default            | Description                                    |
|-----------------|---------------|----------|--------------------|--------------------------------------------------|
| id              | BIGINT        | NO       | AUTO_INCREMENT     | Primary key                                      |
| brand_id        | BIGINT        | NO       |                    | FK → `brand.id`, one row per brand               |
| deposit_spread  | DECIMAL(18,8) | NO       | 0                  | 入金點差 (deposit spread)                        |
| withdraw_spread | DECIMAL(18,8) | NO       | 0                  | 出金點差 (withdraw spread)                       |
| created_at      | DATETIME      | NO       | CURRENT_TIMESTAMP  | Record creation time                             |
| updated_at      | DATETIME      | NO       | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | Last update time    |

### `spread_group`

| Column          | Type          | Nullable | Default            | Description                                    |
|-----------------|---------------|----------|--------------------|--------------------------------------------------|
| id              | BIGINT        | NO       | AUTO_INCREMENT     | Primary key                                      |
| brand_id        | BIGINT        | NO       |                    | FK → `brand.id`                                  |
| name            | VARCHAR(100)  | NO       |                    | Group label, unique per brand                    |
| deposit_spread  | DECIMAL(18,8) | NO       |                    | 入金點差 (deposit spread)                        |
| withdraw_spread | DECIMAL(18,8) | NO       |                    | 出金點差 (withdraw spread)                       |
| created_at      | DATETIME      | NO       | CURRENT_TIMESTAMP  | Record creation time                             |
| updated_at      | DATETIME      | NO       | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | Last update time    |

### `spread_group_member`

| Column            | Type     | Nullable | Default           | Description                                          |
|-------------------|----------|----------|--------------------|--------------------------------------------------------|
| id                | BIGINT   | NO       | AUTO_INCREMENT     | Primary key                                            |
| spread_group_id   | BIGINT   | NO       |                    | FK → `spread_group.id`                                 |
| currency_pair_id  | BIGINT   | NO       |                    | FK → `currency_pair.id`; UNIQUE — a pair belongs to at most one group |
| created_at        | DATETIME | NO       | CURRENT_TIMESTAMP  | When the pair was added to this group                 |

### Indexes / Constraints
- `spread_default`: PK on `id`; UNIQUE on `brand_id`; CHECK `deposit_spread >= 0`; CHECK `withdraw_spread >= 0`; FK `brand_id` → `brand(id)` ON DELETE RESTRICT ON UPDATE RESTRICT.
- `spread_group`: PK on `id`; UNIQUE on (`brand_id`, `name`); CHECK `deposit_spread >= 0`; CHECK `withdraw_spread >= 0`; FK `brand_id` → `brand(id)` ON DELETE RESTRICT ON UPDATE RESTRICT.
- `spread_group_member`: PK on `id`; UNIQUE on `currency_pair_id` (enforces the at-most-one-group invariant); index on `spread_group_id`; FK `spread_group_id` → `spread_group(id)` ON DELETE CASCADE ON UPDATE RESTRICT; FK `currency_pair_id` → `currency_pair(id)` ON DELETE CASCADE ON UPDATE RESTRICT.

Note: nothing at the database level enforces that a member's `currency_pair.brand_id` matches its `spread_group.brand_id` — that cross-table rule is validated by the backend (`specs/backend/spread.md`), consistent with how `base_currency_id <> quote_currency_id` style single-table rules are DB-enforced but cross-table brand-consistency rules are application-enforced elsewhere in this codebase.

## Audit-Approval Addendum
Spread changes (`spread_default` updates, `spread_group` create/update/delete) now go through the existing generic `audit_request` table (`specs/dba/audit.md`, already migrated as `V005`) instead of applying directly — see `specs/backend/spread.md`. **No schema change is needed for this**: `audit_request` is entity-agnostic (`entity_type`/`before_snapshot`/`after_snapshot` already accommodate any new consumer, including `SPREAD_DEFAULT`/`SPREAD_GROUP`, with zero migration). `spread_default`, `spread_group`, and `spread_group_member` themselves are unchanged by this addendum — they are only ever mutated by the backend's audit-handler `apply(...)` step now, never directly.

## Migration SQL

Next migrations in sequence after `V005__create_audit_request_table.sql`.

### `V006__create_spread_default_table.sql`
```sql
-- V006__create_spread_default_table.sql
-- Creates spread_default: one default deposit/withdraw spread row per brand,
-- used whenever a currency pair is not assigned to a custom spread_group.
-- Rollback: DROP TABLE IF EXISTS `spread_default`;

CREATE TABLE IF NOT EXISTS `spread_default` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT,
    `brand_id`        BIGINT         NOT NULL,
    `deposit_spread`  DECIMAL(18,8)  NOT NULL DEFAULT 0,
    `withdraw_spread` DECIMAL(18,8)  NOT NULL DEFAULT 0,
    `created_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spread_default_brand` (`brand_id`),
    CONSTRAINT `ck_spread_default_deposit_nonneg` CHECK (`deposit_spread` >= 0),
    CONSTRAINT `ck_spread_default_withdraw_nonneg` CHECK (`withdraw_spread` >= 0),
    CONSTRAINT `fk_spread_default_brand` FOREIGN KEY (`brand_id`) REFERENCES `brand` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `spread_default` (`brand_id`, `deposit_spread`, `withdraw_spread`)
SELECT `id`, 0, 0 FROM `brand`;
```

### `V007__create_spread_group_table.sql`
```sql
-- V007__create_spread_group_table.sql
-- Creates spread_group: brand-scoped, freely CRUD-able custom spread groups.
-- Rollback: DROP TABLE IF EXISTS `spread_group`;

CREATE TABLE IF NOT EXISTS `spread_group` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT,
    `brand_id`        BIGINT         NOT NULL,
    `name`            VARCHAR(100)   NOT NULL,
    `deposit_spread`  DECIMAL(18,8)  NOT NULL,
    `withdraw_spread` DECIMAL(18,8)  NOT NULL,
    `created_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spread_group_brand_name` (`brand_id`, `name`),
    CONSTRAINT `ck_spread_group_deposit_nonneg` CHECK (`deposit_spread` >= 0),
    CONSTRAINT `ck_spread_group_withdraw_nonneg` CHECK (`withdraw_spread` >= 0),
    CONSTRAINT `fk_spread_group_brand` FOREIGN KEY (`brand_id`) REFERENCES `brand` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### `V008__create_spread_group_member_table.sql`
```sql
-- V008__create_spread_group_member_table.sql
-- Creates spread_group_member: assigns currency pairs into a spread_group.
-- The UNIQUE key on currency_pair_id enforces "a currency pair belongs to
-- at most one spread group" at the database level.
-- Rollback: DROP TABLE IF EXISTS `spread_group_member`;

CREATE TABLE IF NOT EXISTS `spread_group_member` (
    `id`                BIGINT    NOT NULL AUTO_INCREMENT,
    `spread_group_id`   BIGINT    NOT NULL,
    `currency_pair_id`  BIGINT    NOT NULL,
    `created_at`        DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spread_group_member_currency_pair` (`currency_pair_id`),
    KEY `idx_spread_group_member_group` (`spread_group_id`),
    CONSTRAINT `fk_spread_group_member_group` FOREIGN KEY (`spread_group_id`) REFERENCES `spread_group` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `fk_spread_group_member_pair` FOREIGN KEY (`currency_pair_id`) REFERENCES `currency_pair` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Migration Order
1. `V001`–`V005` (already applied)
2. `V006__create_spread_default_table.sql` (this spec) — seeds one zero-value row per existing brand
3. `V007__create_spread_group_table.sql` (this spec)
4. `V008__create_spread_group_member_table.sql` (this spec) — must run after `V007` (FK to `spread_group`) and after `V003` (FK to `currency_pair`)

Apply the same three files to both `develop/backend/src/main/resources/db/migration/` and `docker/mysql/initdb/`, matching the existing dual-location convention for every prior migration.

## Acceptance Criteria
- [x] `spread_default` created with one seeded row per existing brand, `deposit_spread`/`withdraw_spread` both `0`
- [x] UNIQUE constraint on `spread_default.brand_id` (one default row per brand)
- [x] `spread_group` created with UNIQUE (`brand_id`, `name`) and non-negative CHECK constraints on both spread columns
- [x] `spread_group_member` created with UNIQUE `currency_pair_id` — inserting a second membership row for the same currency pair fails
- [x] Deleting a `spread_group` cascades to remove its `spread_group_member` rows
- [x] Deleting a `currency_pair` cascades to remove its `spread_group_member` row, if any
- [x] Attempting to delete a `brand` referenced by `spread_default` or `spread_group` is rejected
- [x] All three migration files applied identically to `develop/backend/src/main/resources/db/migration/` and `docker/mysql/initdb/`
- [x] No new migration is added for the audit-approval addendum — confirmed `audit_request` (`V005`) already accommodates `SPREAD_DEFAULT`/`SPREAD_GROUP` as new `entity_type` values with no schema change

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/backend/src/main/resources/db/migration/V006__create_spread_default_table.sql` (new)
  - `develop/backend/src/main/resources/db/migration/V007__create_spread_group_table.sql` (new)
  - `develop/backend/src/main/resources/db/migration/V008__create_spread_group_member_table.sql` (new)
  - `docker/mysql/initdb/V006__create_spread_default_table.sql` (new, byte-identical to the backend copy — verified via `diff`)
  - `docker/mysql/initdb/V007__create_spread_group_table.sql` (new, byte-identical — verified via `diff`)
  - `docker/mysql/initdb/V008__create_spread_group_member_table.sql` (new, byte-identical — verified via `diff`)
- Notes:
  - Pre-flight passed: connected to MySQL 8.0.36 at `127.0.0.1:3306`, database `wdd`, user `app`; database already existed (7 `brand` rows, 10 `currency` rows, 14 `currency_pair` rows, 2 `audit_request` rows), no creation needed.
  - Applied `V006`, `V007`, `V008` directly against the live `wdd` database via the `mysql` CLI, in order, with no errors.
  - `SHOW TABLES` confirms `spread_default`, `spread_group`, `spread_group_member` now exist alongside the untouched `audit_request`, `brand`, `currency`, `currency_pair`.
  - `DESCRIBE` + `SHOW INDEX` + `SHOW CREATE TABLE` on all three new tables confirm every column, type, nullability, default, PK, UNIQUE key, CHECK constraint, and FK exactly matches the spec (`uk_spread_default_brand`; `uk_spread_group_brand_name` on (`brand_id`,`name`); `uk_spread_group_member_currency_pair` + `idx_spread_group_member_group`; `ck_spread_default_deposit_nonneg`/`ck_spread_default_withdraw_nonneg`; `ck_spread_group_deposit_nonneg`/`ck_spread_group_withdraw_nonneg`; `fk_spread_default_brand`, `fk_spread_group_brand` both `ON DELETE RESTRICT ON UPDATE RESTRICT`; `fk_spread_group_member_group`, `fk_spread_group_member_pair` both `ON DELETE CASCADE ON UPDATE RESTRICT`).
  - Verified seed data: `spread_default` has exactly 7 rows (one per existing brand, `brand_id` 1–7), all with `deposit_spread = 0.00000000` and `withdraw_spread = 0.00000000`.
  - Verified UNIQUE on `spread_group_member.currency_pair_id`: inserted a membership row for `currency_pair_id=1` into one group, then attempted to insert a second membership row for the same `currency_pair_id` into a different group — rejected with `ERROR 1062: Duplicate entry '1' for key 'spread_group_member.uk_spread_group_member_currency_pair'`.
  - Verified CHECK constraints: inserting `spread_group` with `deposit_spread = -1` raised `ERROR 3819: Check constraint 'ck_spread_group_deposit_nonneg' is violated`; updating `spread_default.withdraw_spread` to `-5` raised the equivalent error for `ck_spread_default_withdraw_nonneg`.
  - Verified cascade delete on `spread_group` deletion: deleted a test `spread_group` row that had one member row; the corresponding `spread_group_member` row was automatically removed (table went from 1 row to 0 rows).
  - Verified cascade delete on `currency_pair` deletion: inside a transaction, deleted a `currency_pair` row that had a `spread_group_member` row pointing to it; the membership row count dropped from 1 to 0 immediately after the delete, then rolled back the transaction to restore the original `currency_pair` and membership data (verified restored: counts back to 1/1).
  - Verified FK `RESTRICT` on `brand` deletion is enforced by `spread_default`/`spread_group`, not just by the pre-existing `currency_pair` FK: a direct `DELETE FROM brand WHERE id=1` failed on the pre-existing `fk_currency_pair_brand` constraint (expected, unrelated to this spec); to isolate the new constraint, ran an isolated transaction that first deleted brand 7's `currency_pair` rows (removing that blocker) and then attempted to delete brand 7 itself — this failed specifically on `ERROR 1451: ... CONSTRAINT 'fk_spread_default_brand' ...`, confirming the new table's FK independently enforces the restriction; the transaction was rolled back afterward, leaving `currency_pair`/`brand` row counts unchanged (verified 1/1 for brand 7's pair count and existence).
  - All test/verification rows (`spread_group`, `spread_group_member`) created during verification were deleted, and `spread_default` was reset to all-zero values afterward, leaving the three new tables in their expected post-migration state (7 seeded `spread_default` rows, 0 `spread_group` rows, 0 `spread_group_member` rows).
  - Confirmed pre-existing tables and data are untouched: `brand` (7 rows), `currency` (10 rows), `currency_pair` (14 rows), `audit_request` (2 rows) — same counts as before this migration; no existing migration file (`V001`–`V005`) was modified.
  - No new migration was written for the audit-approval addendum, per spec: `audit_request` (`V005`, already live) is entity-agnostic and requires no schema change to accommodate `SPREAD_DEFAULT`/`SPREAD_GROUP` as new `entity_type` values — confirmed by inspection of the existing `audit_request` schema (no CHECK constraint restricts `entity_type` to known values).
