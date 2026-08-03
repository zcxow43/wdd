---
status: done
title: "Currency Pair Definition (Global Master) Table"
requirement: "幣種對可以被單獨建立, 建立完後所有品牌都有這一個幣種對, 幣種對可以設定正向與反向的精度, 幣種對如果建立正向, 反向就不可被建立"
---

# Currency Pair Definition (Global Master) Table — DBA Spec

## Overview
Adds a brand-agnostic **currency pair master/definition** concept, sitting alongside — not replacing — the existing brand-scoped `currency_pair` table (`specs/dba/currency-pair.md`, already applied, already audit-gated per `specs/backend/currency-pair-approval.md`). A currency pair definition is created once for a (base, quote) direction; the backend (`specs/backend/currency-pair-definition.md`) then provisions a row into the existing `currency_pair` table for every brand. Each definition also carries a decimal-precision setting for its forward direction (base→quote) and its reverse direction (quote→base, computed as `1/rate` and never stored as its own row).

**This is purely additive**: no column is added to, removed from, or altered on `currency_pair`, `brand`, or `currency`. The relationship between a definition and the `currency_pair` rows it provisions is intentionally **not** a foreign key — it's implicit (matching `base_currency_id`/`quote_currency_id` values) — so deleting a definition later never cascades into or touches already-provisioned brand rows.

## Requirements
- New table `currency_pair_definition` holding exactly one row per (base, quote) **direction**.
- If a definition exists for (base=A, quote=B), a definition for (base=B, quote=A) must be rejected — enforced at the database level, not just in application code — per "幣種對如果建立正向, 反向就不可被建立".
- Each definition stores a forward precision (正向精度, decimal places for the base→quote rate) and a reverse precision (反向精度, decimal places for the computed quote→base rate).
- No FK from `currency_pair` to `currency_pair_definition` and no FK the other way — the two tables are related only by the application reusing the same `base_currency_id`/`quote_currency_id` values, per `specs/backend/currency-pair-definition.md`.

## Table Definition

### `currency_pair_definition`

| Column             | Type          | Nullable | Default            | Description                                              |
|--------------------|---------------|----------|---------------------|------------------------------------------------------------|
| id                 | BIGINT        | NO       | AUTO_INCREMENT      | Primary key                                                |
| base_currency_id   | BIGINT        | NO       |                     | FK → `currency.id`                                          |
| quote_currency_id  | BIGINT        | NO       |                     | FK → `currency.id`                                          |
| forward_precision  | TINYINT       | NO       |                     | 正向精度 — decimal places for the base→quote rate, `0`–`8`  |
| reverse_precision  | TINYINT       | NO       |                     | 反向精度 — decimal places for the computed quote→base rate, `0`–`8` |
| pair_key_low       | BIGINT        | NO (generated) |               | `LEAST(base_currency_id, quote_currency_id)` — generated column, direction-independent |
| pair_key_high      | BIGINT        | NO (generated) |               | `GREATEST(base_currency_id, quote_currency_id)` — generated column, direction-independent |
| created_at         | DATETIME      | NO       | CURRENT_TIMESTAMP   | Record creation time                                       |
| updated_at         | DATETIME      | NO       | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | Last update time            |

`pair_key_low`/`pair_key_high` are MySQL **generated columns** (`STORED`, computed from `base_currency_id`/`quote_currency_id`), added purely so a single `UNIQUE` index on the pair of them enforces "this base/quote combination, in either direction, may exist at most once" — inserting the reverse of an existing definition collides on the same generated-column pair and is rejected by the database itself, not just by an application-level pre-check.

### Indexes / Constraints
- PRIMARY KEY on `id`
- UNIQUE KEY on (`pair_key_low`, `pair_key_high`) — the direction-independent, DB-level "no reverse pair" guard
- CHECK constraint: `base_currency_id <> quote_currency_id`
- CHECK constraint: `forward_precision BETWEEN 0 AND 8`
- CHECK constraint: `reverse_precision BETWEEN 0 AND 8`
- FOREIGN KEY `base_currency_id` REFERENCES `currency(id)` ON DELETE RESTRICT ON UPDATE RESTRICT
- FOREIGN KEY `quote_currency_id` REFERENCES `currency(id)` ON DELETE RESTRICT ON UPDATE RESTRICT

## Migration SQL

Next migration in sequence after `V008__create_spread_group_member_table.sql` (`specs/dba/spread-group-member.md`) is `V009__create_currency_pair_definition_table.sql`.

```sql
-- V009__create_currency_pair_definition_table.sql
-- Creates currency_pair_definition: a brand-agnostic master record for a
-- (base, quote) direction, guarding against its reverse direction ever
-- being created, plus forward/reverse rate-display precision. Purely
-- additive — no change to currency_pair/brand/currency. The relationship to
-- currency_pair rows provisioned from a definition (specs/backend/
-- currency-pair-definition.md) is implicit (matching currency ids), not an FK.
-- Rollback: DROP TABLE IF EXISTS `currency_pair_definition`;

CREATE TABLE IF NOT EXISTS `currency_pair_definition` (
    `id`                BIGINT    NOT NULL AUTO_INCREMENT,
    `base_currency_id`  BIGINT    NOT NULL,
    `quote_currency_id` BIGINT    NOT NULL,
    `forward_precision` TINYINT   NOT NULL,
    `reverse_precision` TINYINT   NOT NULL,
    `pair_key_low`  BIGINT AS (LEAST(`base_currency_id`, `quote_currency_id`)) STORED,
    `pair_key_high` BIGINT AS (GREATEST(`base_currency_id`, `quote_currency_id`)) STORED,
    `created_at`        DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_currency_pair_definition_pair_key` (`pair_key_low`, `pair_key_high`),
    CONSTRAINT `ck_currency_pair_definition_distinct` CHECK (`base_currency_id` <> `quote_currency_id`),
    CONSTRAINT `ck_currency_pair_definition_forward_precision` CHECK (`forward_precision` BETWEEN 0 AND 8),
    CONSTRAINT `ck_currency_pair_definition_reverse_precision` CHECK (`reverse_precision` BETWEEN 0 AND 8),
    CONSTRAINT `fk_currency_pair_definition_base` FOREIGN KEY (`base_currency_id`) REFERENCES `currency` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_currency_pair_definition_quote` FOREIGN KEY (`quote_currency_id`) REFERENCES `currency` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

No seed data — definitions are created on demand via the API (`specs/backend/currency-pair-definition.md`), never pre-populated.

## Migration Order
1. `V001`–`V008` (already applied)
2. `V009__create_currency_pair_definition_table.sql` (this spec) — apply directly against the live database when `/dev` executes this spec (see `.claude/agents/dba.md`); no standalone `.sql` file is written anywhere

## Acceptance Criteria
- [x] `currency_pair_definition` created with all columns and correct types, including the two generated columns
- [x] Creating a definition for (base=USD, quote=JPY) succeeds
- [x] Creating a second definition for (base=JPY, quote=USD) — the reverse of the one above — fails at the database level with a unique-key violation on (`pair_key_low`, `pair_key_high`)
- [x] Creating a duplicate definition for the exact same (base=USD, quote=JPY) direction also fails (same unique key)
- [x] CHECK constraints reject `base_currency_id = quote_currency_id`, and `forward_precision`/`reverse_precision` outside `0`–`8`
- [x] Attempting to delete a `currency` row referenced by any `currency_pair_definition` (as base or quote) is rejected
- [x] Deleting a `currency_pair_definition` row succeeds without touching `currency_pair` (no FK/cascade exists between them)
- [x] `currency_pair`, `brand`, and `currency` table definitions are byte-for-byte unchanged by this migration
- [x] Migration applied directly against the live database (historical — at the time this also wrote a copy to `develop/backend/src/main/resources/db/migration/` and `docker/mysql/initdb/`; both locations have since been retired, see Increment 1)

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/backend/src/main/resources/db/migration/V009__create_currency_pair_definition_table.sql` (new)
  - `docker/mysql/initdb/V009__create_currency_pair_definition_table.sql` (new, byte-identical to the backend copy — verified via `diff`)
- Notes:
  - Pre-flight passed: `env.md` had all required `## Database` fields (Engine MySQL 8.0.36, Host `127.0.0.1`, Port `3306`, Database `wdd`, User `app`, Password `1234`); connected successfully via `mysql` CLI; `wdd` database already existed, no creation needed.
  - Confirmed `V006`–`V008` (spread feature) already exist identically in both `develop/backend/src/main/resources/db/migration/` and `docker/mysql/initdb/`, so `V009` was the correct next version for this migration.
  - Applied `V009__create_currency_pair_definition_table.sql` directly against the live `wdd` database via the `mysql` CLI with no errors.
  - `SHOW TABLES` confirms `currency_pair_definition` now exists alongside the untouched `audit_request`, `brand`, `currency`, `currency_pair`, `spread_default`, `spread_group`, `spread_group_member`.
  - `DESCRIBE` + `SHOW INDEX` + `SHOW CREATE TABLE` confirm every column/type/nullability/default, the PK, the `uk_currency_pair_definition_pair_key` UNIQUE index on (`pair_key_low`, `pair_key_high`), all three CHECK constraints (`ck_currency_pair_definition_distinct`, `ck_currency_pair_definition_forward_precision`, `ck_currency_pair_definition_reverse_precision`), and both FKs (`fk_currency_pair_definition_base`, `fk_currency_pair_definition_quote`, both `ON DELETE RESTRICT ON UPDATE RESTRICT`) exactly match the spec. Confirmed MySQL 8.0.36 supports `STORED` generated columns (`LEAST`/`GREATEST` over `base_currency_id`/`quote_currency_id`) combined with a `UNIQUE` index on the generated-column pair — `SHOW CREATE TABLE` shows `pair_key_low`/`pair_key_high` as `GENERATED ALWAYS AS (...) STORED` with the unique key applied directly on them.
  - Verified forward insert: inserted (base=USD id=2, quote=JPY id=4, forward_precision=2, reverse_precision=4); row landed with `pair_key_low=2`, `pair_key_high=4`, confirming `LEAST`/`GREATEST` computed correctly.
  - Verified reverse-direction rejection at the DB level (no application code exists yet in this pipeline stage): inserting (base=JPY id=4, quote=USD id=2) failed with `ERROR 1062: Duplicate entry '2-4' for key 'currency_pair_definition.uk_currency_pair_definition_pair_key'` — the generated-column unique index alone caught the reverse pair.
  - Verified duplicate-exact-direction rejection: re-inserting (base=USD id=2, quote=JPY id=4) failed with the same `ERROR 1062` on the same unique key.
  - Verified CHECK constraints individually: `base_currency_id = quote_currency_id` (e.g. 2,2) raised `ERROR 3819: Check constraint 'ck_currency_pair_definition_distinct' is violated`; `forward_precision=9` raised `ck_currency_pair_definition_forward_precision` violation; `reverse_precision=-1` raised `ck_currency_pair_definition_reverse_precision` violation.
  - Verified FK `RESTRICT` on `currency` deletion is enforced independently by the new table (not just by the pre-existing `currency_pair` FKs): in an isolated transaction, deleted all `currency_pair` rows referencing USD(2)/JPY(4) first (removing the pre-existing `currency_pair` blocker), then attempted `DELETE FROM currency WHERE id=2` — failed specifically on `fk_currency_pair_definition_base`; repeated the same pattern for JPY(4) as the quote side — failed specifically on `fk_currency_pair_definition_quote`. Both transactions were rolled back (the failing `DELETE FROM currency` statement aborted the client batch before `ROLLBACK` ran, but since the transaction was never committed, closing the connection auto-rolled it back); verified afterward that `currency_pair` count was restored to 14 and both `currency` rows (USD, JPY) still existed.
  - Verified deleting a `currency_pair_definition` row does not cascade into or affect `currency_pair`: deleted the test definition row (id=1); `currency_pair` count remained 14 (unchanged) while `currency_pair_definition` dropped from 1 row to 0, confirming no FK/cascade exists between the two tables.
  - Verified `currency_pair`, `brand`, and `currency` table definitions are byte-for-byte unchanged: ran `SHOW CREATE TABLE` on all three post-migration and confirmed no column, index, or constraint was added/removed/altered; no existing migration file (`V001`–`V008`) was modified (confirmed via `ls` timestamps and `git status`).
  - Final sanity check confirms all pre-existing table row counts are unchanged from before this migration: `brand`=7, `currency`=10, `currency_pair`=14, `audit_request`=2, `spread_default`=7, `spread_group`=0, `spread_group_member`=0; `currency_pair_definition`=0 after test-row cleanup (all test/verification rows created during verification were deleted).
  - No seed data was inserted, per spec — `currency_pair_definition` is left empty, ready to be populated on demand via the future backend API.

### Increment 1 — 2026-08-03
- Status: DONE
- Change: retired the `docker/mysql/initdb/` mechanism project-wide — removed its volume mount from `docker/docker-compose.yml`, deleted the `docker/mysql/initdb/` directory (all `V001`–`V011` files), and updated `.claude/agents/dba.md`/`.claude/commands/dev.md` so migration SQL now lives only inside each spec's `## Migration SQL` section and is applied directly against the live database when `/dev` runs — no standalone `.sql` artifact is ever written. No schema or data change; `V009` (already applied) is unaffected.

### Teardown — 2026-08-03
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.

### Increment 2 — 2026-08-03
- Status: DONE
- Files changed: none (no standalone `.sql` file is ever written; migration SQL lives only in this spec's `## Migration SQL` section and was applied directly against the live database)
- Notes:
  - Re-executed from scratch following the prior teardown, per current project convention (migration SQL applied live only — no `develop/backend/src/main/resources/db/migration/` or `docker/mysql/initdb/` copies, both retired per Increment 1).
  - Pre-flight passed: `env.md` had all required `## Database` fields (Engine MySQL 8.0.36, Host `127.0.0.1`, Port `3306`, Database `wdd`, User `app`, Password `1234`); connected successfully via `mysql` CLI; `wdd` database already existed with `V001`–`V008` prerequisite tables (`currency`, `brand`, `currency_pair`, `audit_request`, `spread_default`, `spread_group`, `spread_group_member`) present and `currency_pair_definition` absent, confirming `V009` was correctly next.
  - Applied `V009__create_currency_pair_definition_table.sql` directly against the live `wdd` database via the `mysql` CLI with no errors.
  - `SHOW CREATE TABLE currency_pair_definition` confirms every column/type/nullability/default, the PK, the `uk_currency_pair_definition_pair_key` UNIQUE index on the `GENERATED ALWAYS ... STORED` `pair_key_low`/`pair_key_high` columns (computed via `LEAST`/`GREATEST`), all three CHECK constraints (`ck_currency_pair_definition_distinct`, `ck_currency_pair_definition_forward_precision`, `ck_currency_pair_definition_reverse_precision`), and both FKs (`fk_currency_pair_definition_base`, `fk_currency_pair_definition_quote`, both `ON DELETE RESTRICT ON UPDATE RESTRICT`) exactly match the spec.
  - Verified forward insert (base=USD id=2, quote=JPY id=4, forward_precision=2, reverse_precision=4): row landed with `pair_key_low=2`, `pair_key_high=4`.
  - Verified reverse-direction rejection: inserting (base=JPY id=4, quote=USD id=2) failed with `ERROR 1062: Duplicate entry '2-4' for key 'currency_pair_definition.uk_currency_pair_definition_pair_key'`.
  - Verified duplicate-exact-direction rejection: re-inserting (base=USD id=2, quote=JPY id=4) failed with the same `ERROR 1062` on the same unique key.
  - Verified CHECK constraints individually: `base_currency_id = quote_currency_id` (2,2) raised `ck_currency_pair_definition_distinct` violation (`ERROR 3819`); `forward_precision=9` raised `ck_currency_pair_definition_forward_precision` violation; `reverse_precision=-1` raised `ck_currency_pair_definition_reverse_precision` violation.
  - Verified FK `RESTRICT` is enforced by the new table specifically (isolated from the pre-existing `currency_pair` FKs): in a transaction, deleted the 14 `currency_pair` rows referencing USD(2)/JPY(4), then attempted `DELETE FROM currency WHERE id=2` — failed on `fk_currency_pair_definition_base`; repeated for JPY(4) as quote — failed on `fk_currency_pair_definition_quote`. Both transactions were never committed and the connection close auto-rolled them back; confirmed afterward `currency_pair` count restored to 14 and both `currency` rows (USD, JPY) still existed.
  - Verified deleting a `currency_pair_definition` row does not cascade into `currency_pair`: deleted the test definition row (id=1); `currency_pair_definition` dropped to 0 rows while `currency_pair` remained 14, confirming no FK/cascade exists between the two tables.
  - Verified `currency_pair`, `brand`, and `currency` table definitions are byte-for-byte unchanged: ran `SHOW CREATE TABLE` on all three post-migration and confirmed no column, index, or constraint was added/removed/altered.
  - Final row counts confirm no unintended side effects: `brand`=7, `currency`=10, `currency_pair`=14, `audit_request`=0, `spread_default`=7, `spread_group`=0, `spread_group_member`=0, `currency_pair_definition`=0 after test-row cleanup.
  - No seed data was inserted, per spec — `currency_pair_definition` is left empty, ready to be populated on demand via the backend API.
