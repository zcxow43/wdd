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
- [x] Deleting a `spread_group` cascades to remove its `spread_group_member` rows (`specs/dba/spread-group-member.md`) — re-verified in that spec's 2026-08-04 Increment 4: deleting a `spread_group` row removed its `spread_group_member` row via the FK's `ON DELETE CASCADE`
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

### Teardown — 2026-08-03
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.

### Increment 3 — 2026-08-03
- Status: DONE
- Files changed: none (no standalone `.sql` file written, per current convention — see `.claude/agents/dba.md`). Migration SQL applied directly against the live `wdd` database via the `mysql` CLI.
- Notes:
  - Pre-flight passed: `env.md` `## Database` section complete (MySQL 8.0.36, `127.0.0.1:3306`, db `wdd`, user `app`); connection test (`SELECT 1`) succeeded; target database `wdd` already existed.
  - Confirmed prerequisite tables (`brand`, `currency`, `currency_pair`, `audit_request`, `spread_default`) already exist in the rebuilt database; `spread_group` did not exist yet.
  - Applied `V007__create_spread_group_table.sql` directly via the `mysql` CLI with no errors. `SHOW TABLES` now lists `spread_group` alongside the five pre-existing tables.
  - `DESCRIBE` + `SHOW INDEX` + `SHOW CREATE TABLE` on `spread_group` confirm every column/type/nullability/default, the PK, the `uk_spread_group_brand_name` UNIQUE key on (`brand_id`, `name`), the `ck_spread_group_deposit_nonneg`/`ck_spread_group_withdraw_nonneg` CHECK constraints, and `fk_spread_group_brand` FK to `brand(id)` with `ON DELETE RESTRICT ON UPDATE RESTRICT` — all exactly match the spec.
  - Verified CHECK constraints: inserting rows with `deposit_spread = -1` and `withdraw_spread = -1` each raised `ERROR 3819` for the respective constraint.
  - Verified UNIQUE constraint: inserting two rows with the same (`brand_id`, `name`) raised `ERROR 1062` on `uk_spread_group_brand_name`.
  - Verified FK RESTRICT in isolation: created a dedicated test `brand` row, inserted a `spread_group` row referencing it, then attempted to delete that `brand` inside a transaction — rejected with `ERROR 1451` naming `fk_spread_group_brand` specifically (not a pre-existing FK from another table); transaction rolled back, no lasting change.
  - Cascade-delete criterion (`spread_group` → `spread_group_member`) could not be verified in this increment: `specs/dba/spread-group-member.md` is still `status: pending` and its `spread_group_member` table does not exist in the rebuilt database. Left unchecked in Acceptance Criteria pending that spec's execution.
  - Confirmed `audit_request.entity_type` is a plain `VARCHAR(30)` with no ENUM/CHECK restricting its values — `SPREAD_GROUP` fits as a new value with zero schema change, per the Audit-Approval Addendum. No new migration written for it.
  - All test/verification rows were removed or rolled back afterward; `spread_group` ends this increment at 0 rows (its expected post-migration state — no seed data). Pre-existing row counts (`brand`=7, `currency`=10, `currency_pair`=14, `spread_default`=7, `audit_request`=0) confirmed unchanged before and after.

### Teardown — 2026-08-04
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.

### Increment 4 — 2026-08-04
- Status: DONE
- Files changed: none (no standalone `.sql` file written, per current convention — see `.claude/agents/dba.md`). Migration SQL applied directly against the live `wdd` database via the `mysql` CLI.
- Notes:
  - Pre-flight passed: `env.md` `## Database` section complete (MySQL 8.0.36, `127.0.0.1:3306`, db `wdd`, user `app`); connection test (`SELECT 1`) succeeded; target database `wdd` already existed.
  - Confirmed prerequisite tables (`brand`, `currency`, `currency_pair`, `audit_request`, `spread_default`) already exist in this rebuilt database; `spread_group` did not exist yet.
  - Applied `V007__create_spread_group_table.sql` directly via the `mysql` CLI with no errors. `SHOW TABLES` now lists `spread_group` alongside the five pre-existing tables.
  - `DESCRIBE` + `SHOW INDEX` + `SHOW CREATE TABLE` on `spread_group` confirm every column/type/nullability/default, the PK, the `uk_spread_group_brand_name` UNIQUE key on (`brand_id`, `name`), the `ck_spread_group_deposit_nonneg`/`ck_spread_group_withdraw_nonneg` CHECK constraints, and `fk_spread_group_brand` FK to `brand(id)` with `ON DELETE RESTRICT ON UPDATE RESTRICT` — all exactly match the spec.
  - Verified CHECK constraints: inserting rows with `deposit_spread = -1` and `withdraw_spread = -1` each raised `ERROR 3819` for the respective constraint.
  - Verified UNIQUE constraint: inserting two rows with the same (`brand_id`, `name`) raised `ERROR 1062` on `uk_spread_group_brand_name`.
  - Verified FK RESTRICT in isolation: created a dedicated test `brand` row, inserted a `spread_group` row referencing it, then attempted to delete that `brand` within the same session (no `COMMIT` issued, so the transaction rolled back automatically on session close) — rejected with `ERROR 1451` naming `fk_spread_group_brand` specifically; confirmed no lasting change (test `brand` absent afterward).
  - Cascade-delete criterion (`spread_group` → `spread_group_member`) still could not be verified in this increment: `specs/dba/spread-group-member.md` remains `status: pending` and its `spread_group_member` table does not exist in this rebuilt database. Left unchecked in Acceptance Criteria pending that spec's execution.
  - Confirmed `audit_request.entity_type` is a plain `VARCHAR(30)` with no ENUM/CHECK restricting its values — `SPREAD_GROUP` fits as a new value with zero schema change, per the Audit-Approval Addendum. No new migration written for it.
  - All test/verification rows were removed or rolled back afterward; `spread_group` ends this increment at 0 rows (its expected post-migration state — no seed data). Pre-existing row counts (`brand`=7, `currency`=10, `currency_pair`=14, `spread_default`=7, `audit_request`=0) confirmed unchanged before and after.
