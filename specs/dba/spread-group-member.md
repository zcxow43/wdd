---
status: pending
title: "Spread Group Member Table"
requirement: "客制點差可將多個幣種對加入同一組, 每個幣種對最多屬於一組客制點差"
---

# Spread Group Member Table — DBA Spec

## Overview
Adds the join table assigning `currency_pair` rows into a `spread_group` (`specs/dba/spread-group.md`). Each currency pair can be assigned into **at most one** custom spread group — enforced at the database level via a `UNIQUE` key — guaranteeing "品牌幣種對最多被加入到一組點差中".

Depends on `spread_group` (`specs/dba/spread-group.md`, `V007`) and `currency_pair` (`specs/dba/currency-pair.md`, `V003`), both already migrated.

## Requirements
- `spread_group_member`: join table assigning `currency_pair` rows into a `spread_group`. A `currency_pair_id` may appear in **at most one** membership row at the database level (enforced via a `UNIQUE` key).
- Deleting a `spread_group` removes its memberships (pairs revert to using the default spread); deleting a `currency_pair` removes any membership row for it.

## Table Definition

### `spread_group_member`

| Column            | Type     | Nullable | Default           | Description                                          |
|-------------------|----------|----------|--------------------|--------------------------------------------------------|
| id                | BIGINT   | NO       | AUTO_INCREMENT     | Primary key                                            |
| spread_group_id   | BIGINT   | NO       |                    | FK → `spread_group.id`                                 |
| currency_pair_id  | BIGINT   | NO       |                    | FK → `currency_pair.id`; UNIQUE — a pair belongs to at most one group |
| created_at        | DATETIME | NO       | CURRENT_TIMESTAMP  | When the pair was added to this group                 |

### Indexes / Constraints
- PK on `id`; UNIQUE on `currency_pair_id` (enforces the at-most-one-group invariant); index on `spread_group_id`; FK `spread_group_id` → `spread_group(id)` ON DELETE CASCADE ON UPDATE RESTRICT; FK `currency_pair_id` → `currency_pair(id)` ON DELETE CASCADE ON UPDATE RESTRICT.

Note: nothing at the database level enforces that a member's `currency_pair.brand_id` matches its `spread_group.brand_id` — that cross-table rule is validated by the backend (`specs/backend/spread.md`), consistent with how `base_currency_id <> quote_currency_id` style single-table rules are DB-enforced but cross-table brand-consistency rules are application-enforced elsewhere in this codebase.

## Migration SQL

Next migration in sequence after `V007__create_spread_group_table.sql` (`specs/dba/spread-group.md`) is `V008__create_spread_group_member_table.sql`.

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
1. `V001`–`V007` (already applied, `V007` per `specs/dba/spread-group.md`)
2. `V008__create_spread_group_member_table.sql` (this spec) — must run after `V007` (FK to `spread_group`) and after `V003` (FK to `currency_pair`)

## Acceptance Criteria
- [ ] `spread_group_member` created with UNIQUE `currency_pair_id` — inserting a second membership row for the same currency pair fails
- [ ] Deleting a `spread_group` cascades to remove its `spread_group_member` rows
- [ ] Deleting a `currency_pair` cascades to remove its `spread_group_member` row, if any
- [ ] `V008` applied directly against the live database (per current convention — no `docker/mysql/initdb/` mechanism; see Increment 2 below)

---
## Execution Result
- Status: DONE
- Files changed:
  - `docker/mysql/initdb/V008__create_spread_group_member_table.sql` (new)
- Notes:
  - Applied `V008` directly against the live `wdd` database via the `mysql` CLI, in order after `V006`/`V007`, with no errors.
  - `SHOW TABLES` confirms `spread_group_member` now exists alongside `spread_group`, `spread_default`, `audit_request`, `brand`, `currency`, `currency_pair`.
  - `DESCRIBE` + `SHOW INDEX` + `SHOW CREATE TABLE` on `spread_group_member` confirm every column, type, PK, UNIQUE key, index, and FK exactly matches the spec (`uk_spread_group_member_currency_pair`; `idx_spread_group_member_group`; `fk_spread_group_member_group`, `fk_spread_group_member_pair`, both `ON DELETE CASCADE ON UPDATE RESTRICT`).
  - Verified UNIQUE on `currency_pair_id`: inserted a membership row for `currency_pair_id=1` into one group, then attempted to insert a second membership row for the same `currency_pair_id` into a different group — rejected with `ERROR 1062: Duplicate entry '1' for key 'spread_group_member.uk_spread_group_member_currency_pair'`.
  - Verified cascade delete on `spread_group` deletion: deleted a test `spread_group` row (`specs/dba/spread-group.md`) that had one member row; the corresponding `spread_group_member` row was automatically removed (table went from 1 row to 0 rows).
  - Verified cascade delete on `currency_pair` deletion: inside a transaction, deleted a `currency_pair` row that had a `spread_group_member` row pointing to it; the membership row count dropped from 1 to 0 immediately after the delete, then rolled back the transaction to restore the original `currency_pair` and membership data (verified restored: counts back to 1/1).
  - All test/verification rows were deleted afterward, leaving `spread_group_member` at 0 rows (its expected post-migration state — no seed data for this table).
  - Confirmed pre-existing tables and data are untouched: `brand` (7 rows), `currency` (10 rows), `currency_pair` (14 rows), `audit_request` (2 rows) — same counts as before this migration; no existing migration file (`V001`–`V007`) was modified.

### Increment 1 — 2026-08-03
- Status: DONE
- Change: split out of the former combined `specs/dba/spread.md` into a per-table spec, matching the one-file-per-table convention used by `specs/dba/currency-pair.md`, `specs/dba/currency-pair-definition.md`, etc. No schema or data change — documentation reorganization only.
- Also removed the stale duplicate `develop/backend/src/main/resources/db/migration/V008__create_spread_group_member_table.sql` — the backend has no Flyway/Liquibase dependency, so `docker/mysql/initdb/` is the sole executed source of schema truth (see `.claude/agents/dba.md`). Going forward, migration files are written only to `docker/mysql/initdb/`.

### Increment 2 — 2026-08-03
- Status: DONE
- Change: retired the `docker/mysql/initdb/` mechanism project-wide (superseding Increment 1's note above) — removed its volume mount from `docker/docker-compose.yml`, deleted the `docker/mysql/initdb/` directory (all `V001`–`V011` files), and updated `.claude/agents/dba.md`/`.claude/commands/dev.md` so migration SQL now lives only inside each spec's `## Migration SQL` section and is applied directly against the live database when `/dev` runs — no standalone `.sql` artifact is ever written. No schema or data change; `V008` (already applied) is unaffected.

### Teardown — 2026-08-03
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.

### Increment 3 — 2026-08-03
- Status: DONE
- Change: re-applied `V008__create_spread_group_member_table.sql` directly against the live `wdd` database via the `mysql` CLI (post-teardown rebuild). Confirmed prerequisites `spread_group` (`V007`) and `currency_pair` (`V003`) already existed; `spread_group_member` did not.
- Verification:
  - `SHOW CREATE TABLE` / `DESCRIBE` / `SHOW INDEX` on `spread_group_member` confirm every column, type, PK, UNIQUE key, index, and FK exactly matches the spec (`uk_spread_group_member_currency_pair` on `currency_pair_id`; `idx_spread_group_member_group` on `spread_group_id`; `fk_spread_group_member_group` and `fk_spread_group_member_pair`, both `ON DELETE CASCADE ON UPDATE RESTRICT`).
  - UNIQUE constraint verified: inserted a membership row for `currency_pair_id=1`, then attempted a second membership row for the same `currency_pair_id` into a different group — rejected with `ERROR 1062: Duplicate entry '1' for key 'spread_group_member.uk_spread_group_member_currency_pair'`.
  - Cascade delete on `spread_group` verified: deleted a test `spread_group` row with one member; the corresponding `spread_group_member` row count dropped from 1 to 0 automatically.
  - Cascade delete on `currency_pair` verified inside a transaction: deleted a `currency_pair` row with a `spread_group_member` row pointing to it; member count dropped from 1 to 0; rolled back to restore both `currency_pair` (14 rows) and membership data.
  - All test/verification rows removed afterward; `spread_group_member` left at 0 rows (expected — no seed data for this table).
  - Confirmed pre-existing tables/data untouched: `brand` (7 rows), `currency` (10 rows), `currency_pair` (14 rows), `spread_default` (7 rows), `audit_request` (0 rows), `spread_group` (0 rows after test cleanup).
  - No standalone `.sql` file was written anywhere (per current convention — migration SQL lives only in this spec's `## Migration SQL` section and was applied directly via the `mysql` CLI).

### Teardown — 2026-08-04
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.
