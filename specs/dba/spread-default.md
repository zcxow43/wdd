---
status: done
title: "Spread Default Table"
requirement: "每個品牌有一組預設點差, 有入金出金兩個欄位; 未配置客制點差的幣種對使用該品牌的預設點差; 點差依品牌區分"
---

# Spread Default Table — DBA Spec

## Overview
Adds storage for each brand's **default spread** (預設點差): exactly one row per brand (1:1, seeded), holding `deposit_spread` (入金) and `withdraw_spread` (出金). Any currency pair not assigned to a custom spread group (`specs/dba/spread-group.md`, `specs/dba/spread-group-member.md`) falls back to its brand's default spread. This is intentionally **separate from the existing `brand` table** (`specs/dba/brand.md`), which is a closed, fixed-set feature ("no create/delete, only `active` toggle") — spread defaults are modeled as their own 1:1-per-brand table so that spec's contract is not touched.

Depends on `brand` (`specs/dba/brand.md`), already migrated.

## Requirements
- One `spread_default` row per brand (1:1), holding `deposit_spread` (入金) and `withdraw_spread` (出金); seeded with one zero-value row per existing seeded brand.

## Table Definition

### `spread_default`

| Column          | Type          | Nullable | Default            | Description                                    |
|-----------------|---------------|----------|--------------------|--------------------------------------------------|
| id              | BIGINT        | NO       | AUTO_INCREMENT     | Primary key                                      |
| brand_id        | BIGINT        | NO       |                    | FK → `brand.id`, one row per brand               |
| deposit_spread  | DECIMAL(18,8) | NO       | 0                  | 入金點差 (deposit spread)                        |
| withdraw_spread | DECIMAL(18,8) | NO       | 0                  | 出金點差 (withdraw spread)                       |
| created_at      | DATETIME      | NO       | CURRENT_TIMESTAMP  | Record creation time                             |
| updated_at      | DATETIME      | NO       | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | Last update time    |

### Indexes / Constraints
- PK on `id`; UNIQUE on `brand_id`; CHECK `deposit_spread >= 0`; CHECK `withdraw_spread >= 0`; FK `brand_id` → `brand(id)` ON DELETE RESTRICT ON UPDATE RESTRICT.

## Migration SQL

Next migration in sequence after `V005__create_audit_request_table.sql` is `V006__create_spread_default_table.sql`.

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

## Migration Order
1. `V001`–`V005` (already applied)
2. `V006__create_spread_default_table.sql` (this spec) — seeds one zero-value row per existing brand

## Audit-Approval Addendum
`spread_default` updates now go through the existing generic `audit_request` table (`specs/dba/audit.md`, already migrated as `V005`) instead of applying directly — see `specs/backend/spread.md`. **No schema change is needed for this**: `audit_request` is entity-agnostic (`entity_type`/`before_snapshot`/`after_snapshot` already accommodate a new `SPREAD_DEFAULT` consumer with zero migration). `spread_default` itself is unchanged by this addendum — it is only ever mutated by the backend's audit-handler `apply(...)` step now, never directly.

## Acceptance Criteria
- [x] `spread_default` created with one seeded row per existing brand, `deposit_spread`/`withdraw_spread` both `0`
- [x] UNIQUE constraint on `spread_default.brand_id` (one default row per brand)
- [x] Attempting to delete a `brand` referenced by `spread_default` is rejected
- [x] No new migration is added for the audit-approval addendum — confirmed `audit_request` (`V005`) already accommodates `SPREAD_DEFAULT` as a new `entity_type` value with no schema change

---
## Execution Result
- Status: DONE
- Files changed:
  - `docker/mysql/initdb/V006__create_spread_default_table.sql` (new)
- Notes:
  - Pre-flight passed: connected to MySQL 8.0.36 at `127.0.0.1:3306`, database `wdd`, user `app`; database already existed (7 `brand` rows, 10 `currency` rows, 14 `currency_pair` rows, 2 `audit_request` rows), no creation needed.
  - Applied `V006` directly against the live `wdd` database via the `mysql` CLI, with no errors.
  - `SHOW TABLES` confirms `spread_default` now exists alongside the untouched `audit_request`, `brand`, `currency`, `currency_pair`.
  - `DESCRIBE` + `SHOW INDEX` + `SHOW CREATE TABLE` on `spread_default` confirm every column, type, nullability, default, PK, UNIQUE key, CHECK constraint, and FK exactly matches the spec (`uk_spread_default_brand`; `ck_spread_default_deposit_nonneg`/`ck_spread_default_withdraw_nonneg`; `fk_spread_default_brand` `ON DELETE RESTRICT ON UPDATE RESTRICT`).
  - Verified seed data: `spread_default` has exactly 7 rows (one per existing brand, `brand_id` 1–7), all with `deposit_spread = 0.00000000` and `withdraw_spread = 0.00000000`.
  - Verified CHECK constraint: updating `spread_default.withdraw_spread` to `-5` raised `ERROR 3819: Check constraint 'ck_spread_default_withdraw_nonneg' is violated`.
  - Verified FK `RESTRICT` on `brand` deletion is enforced by `spread_default`, not just by the pre-existing `currency_pair` FK: to isolate the new constraint, ran an isolated transaction that first deleted brand 7's `currency_pair` rows (removing that blocker) and then attempted to delete brand 7 itself — this failed specifically on `ERROR 1451: ... CONSTRAINT 'fk_spread_default_brand' ...`, confirming the new table's FK independently enforces the restriction; the transaction was rolled back afterward.
  - `spread_default` was reset to all-zero values after verification, leaving it in its expected post-migration state (7 seeded rows).
  - Confirmed pre-existing tables and data are untouched: `brand` (7 rows), `currency` (10 rows), `currency_pair` (14 rows), `audit_request` (2 rows) — same counts as before this migration; no existing migration file (`V001`–`V005`) was modified.
  - No new migration was written for the audit-approval addendum, per spec: `audit_request` (`V005`, already live) is entity-agnostic and requires no schema change to accommodate `SPREAD_DEFAULT` as a new `entity_type` value.

### Increment 1 — 2026-08-03
- Status: DONE
- Change: split out of the former combined `specs/dba/spread.md` into a per-table spec, matching the one-file-per-table convention used by `specs/dba/currency-pair.md`, `specs/dba/currency-pair-definition.md`, etc. No schema or data change — documentation reorganization only.
- Also removed the stale duplicate `develop/backend/src/main/resources/db/migration/V006__create_spread_default_table.sql` — the backend has no Flyway/Liquibase dependency, so `docker/mysql/initdb/` is the sole executed source of schema truth (see `.claude/agents/dba.md`). Going forward, migration files are written only to `docker/mysql/initdb/`.

### Increment 2 — 2026-08-03
- Status: DONE
- Change: retired the `docker/mysql/initdb/` mechanism project-wide (superseding Increment 1's note above) — removed its volume mount from `docker/docker-compose.yml`, deleted the `docker/mysql/initdb/` directory (all `V001`–`V011` files), and updated `.claude/agents/dba.md`/`.claude/commands/dev.md` so migration SQL now lives only inside each spec's `## Migration SQL` section and is applied directly against the live database when `/dev` runs — no standalone `.sql` artifact is ever written. No schema or data change; `V006` (already applied) is unaffected.
