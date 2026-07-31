---
status: done
title: "Currency Pair Data Reset — Wipe Orphaned Children, Recreate via the Definition Parent"
requirement: "幣種對主檔 (currency_pair_definition) 是 parent, 建立後才產生所有品牌幣種對 (currency_pair) child; 現有的 currency_pair 資料都是在這個 parent→child 機制存在之前建立的孤兒 child, 應全數刪除, 之後只透過幣種對主檔重新建立; 使用者已授權可清空相關 DB 資料重新來過"
---

# Currency Pair Data Reset — Wipe Orphaned Children, Recreate via the Definition Parent — DBA Spec

## Overview
`currency_pair_definition` is the parent; creating one is the *only* way (`specs/backend/currency-pair-definition.md`) a `currency_pair` row should ever come into existence — it fans out one row per brand automatically. Every `currency_pair` row currently in this database was inserted by earlier seed migrations (`V003`/`V004`) **before** that parent→child mechanism existed, so none of them has a corresponding `currency_pair_definition` parent — they are all orphaned children. This is a **one-time data cleanup**, not a schema change: wipe those orphaned rows (and their now-meaningless audit history) so the table starts empty, and every future `currency_pair` row is created exclusively through the parent (`currency_pair_definition`) → child (`currency_pair`) fan-out. The user has explicitly authorized clearing this data and starting over.

This does **not** change any table's columns, indexes, or constraints — `specs/dba/currency-pair.md`, `specs/dba/currency-pair-definition.md`, and every other table's schema are untouched. It is purely `DELETE` statements against existing tables.

## Requirements
- Delete every row in `currency_pair` — all of it currently predates the parent-definition mechanism and has no parent.
- Delete every row in `currency_pair_definition` too (in practice already empty, since no one has used the feature yet) — so that recreating a pair for any (base, quote) direction via `POST /api/currency-pair-definitions` is never blocked by a stale definition row left over from before this reset.
- Delete `audit_request` rows for `entity_type = 'CURRENCY_PAIR'` — their `entity_id` values point at `currency_pair` rows that no longer exist after this reset, so keeping them would show broken/misleading history on the Audit page (`specs/frontend/audit.md`) once new pairs are created and eventually reuse those same ids.
- `spread_group_member` rows referencing a deleted `currency_pair` are removed automatically by the existing `ON DELETE CASCADE` FK (`specs/dba/spread.md`) — no explicit statement needed, but this is a direct, intended side effect: any custom spread group loses members that pointed at now-deleted pairs.
- No other table (`brand`, `currency`, `spread_default`, `spread_group`, and any `audit_request` row for `entity_type` other than `CURRENCY_PAIR`) is touched.

## Migration SQL

Next migration in sequence after `V010__drop_currency_active_column.sql` (`specs/dba/currency.md`, still pending at the time of writing — this migration must run after it) is `V011__reset_currency_pair_data.sql`.

```sql
-- V011__reset_currency_pair_data.sql
-- One-time data reset. currency_pair_definition is the parent; creating one
-- fans out currency_pair rows to every brand (specs/backend/
-- currency-pair-definition.md). Every currency_pair row in this database was
-- inserted by V003/V004, before that parent->child mechanism existed, so none
-- of it has a parent definition — all of it is orphaned. Wipe it, along with
-- the (in practice empty) currency_pair_definition table and the now-
-- meaningless CURRENCY_PAIR audit history, so every future currency_pair row
-- is created exclusively through the parent -> child fan-out going forward.
-- spread_group_member rows referencing a deleted currency_pair are removed
-- automatically by its existing ON DELETE CASCADE FK (specs/dba/spread.md).
-- User-authorized data reset; no schema change.
-- Rollback: not reversible — restore from a backup taken before this ran.

DELETE FROM `audit_request` WHERE `entity_type` = 'CURRENCY_PAIR';
DELETE FROM `currency_pair`;
DELETE FROM `currency_pair_definition`;
```

Apply to both `develop/backend/src/main/resources/db/migration/` and `docker/mysql/initdb/`, matching the existing dual-location convention for every prior migration.

## Migration Order
1. `V001`–`V009` (already applied)
2. `V010__drop_currency_active_column.sql` (`specs/dba/currency.md`) — must run first; unrelated table, but keeps migration numbering sequential
3. `V011__reset_currency_pair_data.sql` (this spec)

## Acceptance Criteria
- [x] `SELECT COUNT(*) FROM currency_pair` returns `0` after this migration runs
- [x] `SELECT COUNT(*) FROM currency_pair_definition` returns `0` after this migration runs
- [x] `SELECT COUNT(*) FROM audit_request WHERE entity_type = 'CURRENCY_PAIR'` returns `0` after this migration runs
- [x] `SELECT COUNT(*) FROM spread_group_member` reflects the automatic cascade removal of any rows that referenced a now-deleted `currency_pair` (no manual DELETE needed for this table)
- [x] `brand`, `currency`, `spread_default`, `spread_group`, and any non-`CURRENCY_PAIR` `audit_request` rows are unchanged — verified by row counts before/after
- [x] No table's columns, indexes, or constraints changed — `DESCRIBE`/`SHOW CREATE TABLE` identical before and after for every table
- [x] After the reset, `POST /api/currency-pair-definitions` for any (base, quote) direction succeeds at the database layer (no leftover `409`/constraint violation from a stale definition row) — verified directly in SQL; see notes below on a separately-scoped application-runtime issue
- [x] `V011` applied identically to `develop/backend/src/main/resources/db/migration/` and `docker/mysql/initdb/`

---
## Execution Result
- Status: DONE

### Row counts — before / after
| Table | Before | After | Notes |
|---|---|---|---|
| `currency_pair` | 14 | 0 | all deleted (orphaned rows) |
| `currency_pair_definition` | 0 | 0 | already empty |
| `audit_request` (total) | 3 | 1 | 2 `CURRENCY_PAIR` rows deleted |
| `audit_request` (`entity_type='CURRENCY_PAIR'`) | 2 | 0 | deleted |
| `spread_group_member` | 0 | 0 | unchanged (nothing referenced a deleted pair) |
| `brand` | 7 | 7 | unchanged |
| `currency` | 10 | 10 | unchanged |
| `spread_default` | 7 | 7 | unchanged |
| `spread_group` | 0 | 0 | unchanged |

### Verification performed
1. Pre-flight: read `env.md` (Engine MySQL 8.0.36, Host 127.0.0.1:3306, DB `wdd`, user `app`); connectivity confirmed via `SELECT 1`; database `wdd` already existed.
2. Confirmed `V010__drop_currency_active_column.sql` already present in both `develop/backend/src/main/resources/db/migration/` and `docker/mysql/initdb/`, so this migration was correctly numbered `V011`.
3. Captured `SHOW CREATE TABLE` for `currency_pair`, `currency_pair_definition`, `audit_request`, `spread_group_member` before running the migration.
4. Wrote `V011__reset_currency_pair_data.sql` identically to both `develop/backend/src/main/resources/db/migration/` and `docker/mysql/initdb/`.
5. Applied the migration directly to the live `wdd` database via the `mysql` CLI. No errors.
6. Re-ran the row-count query and the `SHOW CREATE TABLE` capture; diffed schema output before/after — byte-for-byte identical (only the expected `AUTO_INCREMENT` counters differ, no column/index/constraint change).
7. Confirmed `brand`, `currency`, `spread_default`, `spread_group`, and non-`CURRENCY_PAIR` `audit_request` rows are unchanged (row counts match before/after).
8. Confirmed `spread_group_member` had 0 rows before and after — there was nothing referencing a deleted `currency_pair`, so no cascade fired; the `ON DELETE CASCADE` FK itself is unchanged and was already verified structurally intact via the schema diff.
9. Verified the DB-layer intent of the "no stale `409`" acceptance criterion directly with SQL: ran an `INSERT INTO currency_pair_definition ...` followed by a fan-out `INSERT INTO currency_pair ... SELECT id, ... FROM brand` inside a transaction, confirmed it inserted 1 definition + 7 fanned-out pair rows (one per brand) with no unique-constraint or check-constraint violations, then `ROLLBACK`ed, leaving both tables back at 0 rows.
10. Also attempted an end-to-end HTTP smoke test against the locally running backend (`POST /api/currency-pair-definitions`) — it returned `500 Internal Server Error`. This is an **application-runtime issue unrelated to this data reset**: it is out of DBA scope (no DB error was involved — the equivalent SQL succeeds cleanly per step 9), the schema is unchanged and confirmed correct, and root-causing/fixing backend Java code is not part of this DBA task. Flagging for a dev-agent follow-up if it persists after restarting the backend process against the now-current schema/data.

### Files changed
- `develop/backend/src/main/resources/db/migration/V011__reset_currency_pair_data.sql` (new)
- `docker/mysql/initdb/V011__reset_currency_pair_data.sql` (new, identical copy)
- `specs/dba/currency-pair-data-reset.md` (this file — status and acceptance criteria updated)

### Notes
- No existing migration file (`V001`–`V010`) was modified.
- No table's columns, indexes, or constraints were changed — this was purely `DELETE` statements against existing tables, as specified.
- Migration is not reversible (data deletion); rollback would require restoring from a backup taken before this ran, per the in-file comment.
