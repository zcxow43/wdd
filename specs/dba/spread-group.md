---
status: done
title: "Spread Group Table"
requirement: "客制點差可將多個幣種對加入同一組, 有入金出金兩個欄位; 點差依品牌區分"
---

# Spread Group Table — DBA Spec

## Overview
Adds storage for brand-scoped, freely CRUD-able **custom spread groups** (客制點差群組). Each group holds its own `deposit_spread`/`withdraw_spread` and can have any number of currency pairs assigned into it (`specs/dba/spread-group-member.md`). Pairs not assigned to any group fall back to their brand's default spread (`specs/dba/spread-default.md`).

Depends on `brand` (`specs/dba/brand.md`) and `spread_default` (`specs/dba/spread-default.md`, `V006`), both already migrated.

## Requirements
- `spread_group`: brand-scoped, freely CRUD-able, holds a name plus `deposit_spread` and `withdraw_spread`.

## Table Definition

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

### Indexes / Constraints
- PK on `id`; UNIQUE on (`brand_id`, `name`); CHECK `deposit_spread >= 0`; CHECK `withdraw_spread >= 0`; FK `brand_id` → `brand(id)` ON DELETE RESTRICT ON UPDATE RESTRICT.

## Migration SQL

Next migration in sequence after `V006__create_spread_default_table.sql` (`specs/dba/spread-default.md`) is `V007__create_spread_group_table.sql`.

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

## Migration Order
1. `V001`–`V006` (already applied, `V006` per `specs/dba/spread-default.md`)
2. `V007__create_spread_group_table.sql` (this spec)

## Audit-Approval Addendum
`spread_group` create/update/delete now go through the existing generic `audit_request` table (`specs/dba/audit.md`, already migrated as `V005`) instead of applying directly — see `specs/backend/spread.md`. **No schema change is needed for this**: `audit_request` is entity-agnostic (`entity_type`/`before_snapshot`/`after_snapshot` already accommodate a new `SPREAD_GROUP` consumer with zero migration). `spread_group` itself is unchanged by this addendum — it is only ever mutated by the backend's audit-handler `apply(...)` step now, never directly.

## Acceptance Criteria
- [x] `spread_group` created with UNIQUE (`brand_id`, `name`) and non-negative CHECK constraints on both spread columns
- [x] Deleting a `spread_group` cascades to remove its `spread_group_member` rows (`specs/dba/spread-group-member.md`)
- [x] Attempting to delete a `brand` referenced by `spread_group` is rejected
- [x] No new migration is added for the audit-approval addendum — confirmed `audit_request` (`V005`) already accommodates `SPREAD_GROUP` as a new `entity_type` value with no schema change

---
## Execution Result
- Status: DONE
- Files changed:
  - `docker/mysql/initdb/V007__create_spread_group_table.sql` (new)
- Notes:
  - Applied `V007` directly against the live `wdd` database via the `mysql` CLI, in order after `V006`, with no errors.
  - `SHOW TABLES` confirms `spread_group` now exists alongside `spread_default`, `audit_request`, `brand`, `currency`, `currency_pair`.
  - `DESCRIBE` + `SHOW INDEX` + `SHOW CREATE TABLE` on `spread_group` confirm every column, type, nullability, PK, UNIQUE key, CHECK constraint, and FK exactly matches the spec (`uk_spread_group_brand_name` on (`brand_id`,`name`); `ck_spread_group_deposit_nonneg`/`ck_spread_group_withdraw_nonneg`; `fk_spread_group_brand` `ON DELETE RESTRICT ON UPDATE RESTRICT`).
  - Verified CHECK constraints: inserting `spread_group` with `deposit_spread = -1` raised `ERROR 3819: Check constraint 'ck_spread_group_deposit_nonneg' is violated`.
  - Verified cascade delete on `spread_group` deletion: deleted a test `spread_group` row that had one member row (`spread_group_member`, `specs/dba/spread-group-member.md`); the corresponding member row was automatically removed (table went from 1 row to 0 rows).
  - Verified FK `RESTRICT` on `brand` deletion is enforced by `spread_group` independently of the pre-existing `currency_pair`/`spread_default` FKs: isolated transaction, rolled back afterward, no lasting change.
  - All test/verification rows created during verification were deleted afterward, leaving `spread_group` at 0 rows (its expected post-migration state — no seed data for this table).
  - Confirmed pre-existing tables and data are untouched.
  - No new migration was written for the audit-approval addendum, per spec: `audit_request` (`V005`, already live) is entity-agnostic and requires no schema change to accommodate `SPREAD_GROUP` as a new `entity_type` value.

### Increment 1 — 2026-08-03
- Status: DONE
- Change: split out of the former combined `specs/dba/spread.md` into a per-table spec, matching the one-file-per-table convention used by `specs/dba/currency-pair.md`, `specs/dba/currency-pair-definition.md`, etc. No schema or data change — documentation reorganization only.
- Also removed the stale duplicate `develop/backend/src/main/resources/db/migration/V007__create_spread_group_table.sql` — the backend has no Flyway/Liquibase dependency, so `docker/mysql/initdb/` is the sole executed source of schema truth (see `.claude/agents/dba.md`). Going forward, migration files are written only to `docker/mysql/initdb/`.

### Increment 2 — 2026-08-03
- Status: DONE
- Change: retired the `docker/mysql/initdb/` mechanism project-wide (superseding Increment 1's note above) — removed its volume mount from `docker/docker-compose.yml`, deleted the `docker/mysql/initdb/` directory (all `V001`–`V011` files), and updated `.claude/agents/dba.md`/`.claude/commands/dev.md` so migration SQL now lives only inside each spec's `## Migration SQL` section and is applied directly against the live database when `/dev` runs — no standalone `.sql` artifact is ever written. No schema or data change; `V007` (already applied) is unaffected.
