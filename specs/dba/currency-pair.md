---
status: done
title: "Currency Pair Table (Brand-Scoped)"
requirement: "新增品牌幣種對：每個品牌各自的幣種對設定（自動/手動匯率、開啟關閉），幣種對新增時自動為所有品牌建立一筆，預設關閉且為自動匯率"
---

# Currency Pair — DBA Spec

## Overview
`currency_pair` is each brand's own settings for a given currency pair definition — whether it's enabled, and whether its rate is automatic or a manually-entered value. One row per `(currency_pair_definition, brand)` combination. Rows are primarily created by the fan-out in [currency-pair-definition.md](../backend/currency-pair-definition.md) (one per existing brand whenever a definition is created), but the API also supports creating/deleting individual rows directly (see [currency-pair.md](../backend/currency-pair.md)).

This table also owns the **spread group membership** link: `spread_group_id` is the single nullable column that says which [spread group](spread-group.md) this brand currency pair belongs to. Because membership is one column on the pair rather than a join table, "每個品牌幣種對只能加入一個群組" is structurally guaranteed — a pair cannot hold two group ids. A `NULL` means the pair uses its brand's default spread from [brand-spread.md](brand-spread.md) instead.

## Requirements
- One table: `currency_pair`.
- Exactly one row per `(currency_pair_definition_id, brand_id)` pair — enforced by a unique constraint.
- `rate_type` is `AUTO` or `MANUAL`; `rate` pairs with it (validated at the application layer, not by a DB CHECK, matching this project's existing convention of keeping conditional field validation in the service layer).
- `active` defaults to `false` — a newly created row (whether via fan-out or direct creation) starts disabled.
- `spread_group_id` is nullable and references `spread_group.id`. It may only point at a group belonging to the **same brand** as the row itself — cross-brand assignment is rejected at the application layer (matching this project's convention of keeping cross-row conditional validation in the service layer, since a DB constraint cannot express it without denormalizing `brand_id`).
- Deleting a `spread_group` sets its members' `spread_group_id` back to `NULL` rather than deleting the pairs — they fall back to the brand default spread.
- Deleting the parent `currency_pair_definition` cascades to delete all of its `currency_pair` rows — only reachable after the definition-level delete guard (in its own backend spec) has confirmed none of them are `active`.

## Implementation Details

### Table: `currency_pair`

| Column | Type | Constraint |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| currency_pair_definition_id | BIGINT | NOT NULL, FK → `currency_pair_definition.id`, ON DELETE CASCADE |
| brand_id | BIGINT | NOT NULL, FK → `brand.id` |
| rate_type | ENUM('AUTO','MANUAL') | NOT NULL, DEFAULT 'AUTO' |
| rate | DECIMAL(18,8) | NULL |
| active | BOOLEAN | NOT NULL, DEFAULT FALSE |
| spread_group_id | BIGINT | NULL, FK → `spread_group.id`, ON DELETE SET NULL |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

## Migration SQL — V005__create_currency_pair.sql

Comes after `V004__create_currency_pair_definition.sql` (`specs/dba/currency-pair-definition.md`) — must run after it since this table FKs to it.

```sql
CREATE TABLE currency_pair (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    currency_pair_definition_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    rate_type ENUM('AUTO','MANUAL') NOT NULL DEFAULT 'AUTO',
    rate DECIMAL(18,8) NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_currency_pair UNIQUE (currency_pair_definition_id, brand_id),
    CONSTRAINT fk_currency_pair_definition FOREIGN KEY (currency_pair_definition_id) REFERENCES currency_pair_definition(id) ON DELETE CASCADE,
    CONSTRAINT fk_currency_pair_brand FOREIGN KEY (brand_id) REFERENCES brand(id)
);
```

## Migration SQL — V008__add_spread_group_to_currency_pair.sql (Delta: spread group membership)

Comes after `V007__create_spread_group.sql` (`specs/dba/spread-group.md`) — this ALTER adds the FK that points at that table, so `spread_group` must already exist. Note this file owns two migrations, `V005` and `V008`; `V005` still runs at its own position (right after `V004`), and this `V008` runs only after `V006` and `V007` have been applied.

```sql
ALTER TABLE currency_pair
    ADD COLUMN spread_group_id BIGINT NULL AFTER active,
    ADD CONSTRAINT fk_currency_pair_spread_group
        FOREIGN KEY (spread_group_id) REFERENCES spread_group(id) ON DELETE SET NULL;
```

## Acceptance Criteria
- [x] `currency_pair` table exists with columns exactly as defined above.
- [x] Unique constraint on `(currency_pair_definition_id, brand_id)`.
- [x] `currency_pair_definition_id` foreign key has `ON DELETE CASCADE`.
- [x] `brand_id` foreign keys to `brand.id`.
- [x] `rate_type` defaults to `AUTO`, `active` defaults to `false`.
- [x] `spread_group_id` column exists on `currency_pair`, is nullable, and defaults to `NULL` for both existing and newly created rows.
- [x] `fk_currency_pair_spread_group` foreign key references `spread_group.id` with `ON DELETE SET NULL` — deleting a group nulls its members' `spread_group_id` and deletes no `currency_pair` rows.

---
## Execution Result
- Status: DONE
- Files changed: `specs/dba/currency-pair.md` (this file — migration SQL executed live, frontmatter status updated, acceptance criteria checked off)
- Notes: Pre-flight validated `env.md` (MySQL 8.0.36, 127.0.0.1:3306, db `wdd`, user `app`) and confirmed connectivity plus existing `wdd` database. Confirmed prerequisite tables `currency_pair_definition` (V004) and `brand` already present. Executed V005 DDL directly via `mysql` CLI against the live `wdd` database — no standalone `.sql` file was created. Verified via `DESCRIBE`/`SHOW CREATE TABLE` that the table matches the spec exactly: PK `id`, unique key `uk_currency_pair` on `(currency_pair_definition_id, brand_id)`, `fk_currency_pair_definition` with `ON DELETE CASCADE` to `currency_pair_definition(id)`, `fk_currency_pair_brand` to `brand(id)`, `rate_type` ENUM default `'AUTO'`, `active` default `0` (false). No application code changes were made — this spec is DBA-only (table creation); the fan-out on definition create and the AUTO/MANUAL+rate validation are handled by the corresponding backend spec, not here.

### Increment 2 — 2026-08-22
- Trigger: live `wdd` database was reset (fresh volume); `brand` (V001), `currency` (V002/V003), and `currency_pair_definition` (V004) had already been re-applied by prior specs, but `currency_pair` (V005) did not yet exist. Re-ran this spec's migration on explicit instruction to re-apply every DBA spec's SQL regardless of `status: done`.
- Pre-flight: read `env.md` (MySQL 8.0.36, 127.0.0.1:3306, db `wdd`, user `app`, password `1234`); `mysql -h 127.0.0.1 -P 3306 -u app -p1234 -e "SELECT 1;"` succeeded; `SHOW DATABASES LIKE 'wdd';` confirmed the database exists — no creation needed.
- Confirmed via `SHOW TABLES` that `currency_pair` was absent while `brand`, `currency`, `currency_pair_definition` were present.
- Applied the V005 SQL (identical to the block above, all-ASCII) by writing it to a UTF-8 scratch file and piping it in with `mysql --default-character-set=utf8mb4 wdd < file.sql` — executed cleanly, no errors.
- Verified via `SHOW CREATE TABLE currency_pair` / `DESCRIBE currency_pair` / `information_schema.TABLE_CONSTRAINTS` that the live table exactly matches the spec: PK `id` AUTO_INCREMENT; `currency_pair_definition_id` BIGINT NOT NULL; `brand_id` BIGINT NOT NULL; `rate_type` ENUM('AUTO','MANUAL') NOT NULL DEFAULT 'AUTO'; `rate` DECIMAL(18,8) NULL; `active` tinyint(1)/BOOLEAN NOT NULL DEFAULT 0; `created_at`/`updated_at` TIMESTAMP with correct defaults; unique key `uk_currency_pair` on `(currency_pair_definition_id, brand_id)`; `fk_currency_pair_definition` → `currency_pair_definition(id)` ON DELETE CASCADE; `fk_currency_pair_brand` → `brand(id)`.
- No changes made to this spec's frontmatter or Acceptance Criteria checkboxes, per instruction. No standalone `.sql` file was left behind (scratch file lives only under the session temp scratchpad, outside the repo).

### Increment 3 — 2026-08-23
- Trigger: implement the delta migration `V008__add_spread_group_to_currency_pair.sql` — the two remaining unchecked Acceptance Criteria (`spread_group_id` column + `fk_currency_pair_spread_group` FK with `ON DELETE SET NULL`). Prerequisites `V006` (`brand_spread`) and `V007` (`spread_group`) were confirmed already applied; `spread_group` existed and was empty.
- Pre-flight: read `env.md` (MySQL 8.0.36, 127.0.0.1:3306, db `wdd`, user `app`, password `1234`); `mysql -h 127.0.0.1 -P 3306 -u app -p1234 -e "SELECT 1;"` succeeded; `SHOW DATABASES LIKE 'wdd';` confirmed the database exists — no creation needed.
- Confirmed via `DESCRIBE currency_pair` that the table existed (from V005, already applied) without a `spread_group_id` column, and that `spread_group` existed per V007.
- Applied the V008 SQL (identical to the block above) by writing it to a UTF-8 scratch file and piping it in with `mysql --default-character-set=utf8mb4 wdd < file.sql` — executed cleanly, no errors. Did not touch or re-run V005.
- Verified via `DESCRIBE currency_pair` and `SHOW CREATE TABLE currency_pair` that `spread_group_id` is `bigint`, nullable (`YES`), `DEFAULT NULL`, positioned after `active`, and that `fk_currency_pair_spread_group` is present. Cross-checked `information_schema.REFERENTIAL_CONSTRAINTS` joined to `KEY_COLUMN_USAGE`, confirming `fk_currency_pair_spread_group` → `spread_group(id)` with `DELETE_RULE = SET NULL` (alongside the pre-existing `fk_currency_pair_definition` CASCADE and `fk_currency_pair_brand` NO ACTION, both unchanged).
- Proved the `SET NULL` behavior end-to-end with real rows: inserted a temporary `currency_pair_definition` (base=USD id 1, quote=JPY id 2), a temporary `spread_group` (brand_id 1, name `TEMP_TEST_GROUP`), and a temporary `currency_pair` row referencing both, with `spread_group_id` set to the new group's id. Confirmed the pair row held the non-null `spread_group_id` before deletion. Deleted the `spread_group` row directly. Re-queried the `currency_pair` row by id: it still existed (not cascade-deleted) and its `spread_group_id` had been automatically set to `NULL`. Confirmed the `spread_group` row itself was gone.
- Cleanup: deleted the temporary `currency_pair` row and the temporary `currency_pair_definition` row (the `spread_group` row was already removed by the test itself). Re-checked row counts across `currency_pair`, `spread_group`, `currency_pair_definition`, `currency`, and `brand` — all back to their pre-test values (0, 0, 0, 5, 7 respectively; `currency` and `brand` untouched throughout).
- Checked off both previously-unchecked Acceptance Criteria items; left the five already-checked items and prior Execution Result / Increment 2 history untouched. No standalone `.sql` file was left behind (scratch files live only under the session temp scratchpad, outside the repo). No application code changes were made — this spec is DBA-only.
