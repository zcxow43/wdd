---
status: done
title: "Audit Request Table"
requirement: "品牌幣種對與點差的新增/修改/刪除動作需要審核通過才會生效；此表存放待審與已審的申請紀錄"
---

# Audit Request — DBA Spec

## Overview
`audit_request` is the single log of every change that has been **requested but not yet applied** to an audited entity, plus the historical record of every request that was approved, rejected, or withdrawn. Today the audited entities are 品牌幣種對 (`currency_pair`) and 點差 (`brand_spread`, `spread_group`, and group membership) — see [audit.md](../backend/audit.md) — but nothing in this table's shape is specific to them: the target is identified by a generic `(entity_type, entity_id)` pair and the change itself is carried as JSON, so a new audited entity needs no schema change here.

## Requirements
- One table: `audit_request`.
- **Deliberately FK-isolated — this table has no foreign keys to anything.** That is the point, not an oversight: an approved `DELETE` removes the row it refers to, and a cascade can remove a `currency_pair` out from under a historical request. An audit log that could be cascaded away, or that blocked a delete, would be worthless. `entity_id` and `brand_id` are therefore plain columns that may point at rows that no longer exist.
- `entity_id` is `NULL` for a `CREATE` request (the row does not exist yet) and set for `UPDATE`/`DELETE`.
- `before_data` is `NULL` for `CREATE` (nothing existed); `after_data` is `NULL` for `DELETE` (nothing will remain). Both are JSON snapshots so a reviewer can see exactly what changes without the reviewing code understanding the entity.
- **At most one `PENDING` request may exist per target row.** Two pending edits to the same brand currency pair would mean whichever is approved second silently overwrites the first based on stale data. Enforced in the database, not only in the application — see the generated `pending_key` column below.
- A request is terminal once it leaves `PENDING`: `APPROVED`, `REJECTED`, and `CANCELLED` rows are never edited again, so history stays truthful.
- `apply_error` records why an approval failed to apply (the underlying data drifted since the request was raised). The request stays `PENDING` in that case so it can be retried or withdrawn.

## Implementation Details

### Table: `audit_request`

| Column | Type | Constraint |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| entity_type | VARCHAR(40) | NOT NULL — `CURRENCY_PAIR` / `BRAND_SPREAD` / `SPREAD_GROUP` / `SPREAD_GROUP_MEMBER` |
| action_type | ENUM('CREATE','UPDATE','DELETE') | NOT NULL |
| entity_id | BIGINT | NULL — target row id; NULL for `CREATE`. No FK by design |
| brand_id | BIGINT | NULL — denormalized so the review list can filter/display by brand without joining a table the request may outlive. No FK by design |
| summary | VARCHAR(200) | NOT NULL — one-line human-readable description of the requested change |
| before_data | JSON | NULL — the target row as it was when the request was raised; NULL for `CREATE` |
| after_data | JSON | NULL — the requested new values; NULL for `DELETE` |
| status | ENUM('PENDING','APPROVED','REJECTED','CANCELLED') | NOT NULL, DEFAULT 'PENDING' |
| requested_by | VARCHAR(50) | NOT NULL |
| requested_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| reviewed_by | VARCHAR(50) | NULL |
| reviewed_at | TIMESTAMP | NULL |
| review_comment | VARCHAR(500) | NULL |
| apply_error | VARCHAR(500) | NULL |
| pending_key | VARCHAR(64) | GENERATED, STORED, UNIQUE — see below |

### The one-pending-per-target constraint

MySQL has no partial/filtered unique index, so "unique only while `PENDING`" is expressed with a stored generated column that is `NULL` for every non-pending row — and MySQL's unique indexes permit unlimited `NULL`s:

```
pending_key = CASE WHEN status = 'PENDING' AND entity_id IS NOT NULL
                   THEN CONCAT(entity_type, ':', entity_id)
                   ELSE NULL END
```

So two `PENDING` requests against the same target collide on `uk_audit_request_pending`, while any number of historical rows for that same target coexist freely. `CREATE` requests have no `entity_id` and are therefore never constrained — several new rows may legitimately be pending at once.

### Indexes
- `uk_audit_request_pending` UNIQUE on `pending_key`.
- `idx_audit_request_status_time` on `(status, requested_at DESC)` — the review queue's default query.
- `idx_audit_request_entity` on `(entity_type, entity_id)` — "does this row have a pending request?" and per-row history.
- `idx_audit_request_brand` on `(brand_id)` — brand-scoped filtering.

## Migration SQL — V009__create_audit_request.sql

Comes after `V008__add_spread_group_to_currency_pair.sql` (`specs/dba/currency-pair.md`) — the highest version applied so far. It has no FK dependency on any other table by design, so it only needs to run after the database exists.

```sql
CREATE TABLE audit_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(40) NOT NULL,
    action_type ENUM('CREATE','UPDATE','DELETE') NOT NULL,
    entity_id BIGINT NULL,
    brand_id BIGINT NULL,
    summary VARCHAR(200) NOT NULL,
    before_data JSON NULL,
    after_data JSON NULL,
    status ENUM('PENDING','APPROVED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    requested_by VARCHAR(50) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by VARCHAR(50) NULL,
    reviewed_at TIMESTAMP NULL,
    review_comment VARCHAR(500) NULL,
    apply_error VARCHAR(500) NULL,
    pending_key VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' AND entity_id IS NOT NULL
             THEN CONCAT(entity_type, ':', entity_id)
             ELSE NULL END
    ) STORED,
    CONSTRAINT uk_audit_request_pending UNIQUE (pending_key)
);

CREATE INDEX idx_audit_request_status_time ON audit_request (status, requested_at DESC);
CREATE INDEX idx_audit_request_entity ON audit_request (entity_type, entity_id);
CREATE INDEX idx_audit_request_brand ON audit_request (brand_id);
```

No seed data — every row is created by a user action.

## Acceptance Criteria
- [x] `audit_request` table exists with columns exactly as defined above.
- [x] The table has **no** foreign keys — verified against `information_schema.TABLE_CONSTRAINTS`.
- [x] `pending_key` is a stored generated column producing `<entity_type>:<entity_id>` only while `status = 'PENDING'` and `entity_id IS NOT NULL`, and `NULL` otherwise.
- [x] Inserting a second `PENDING` row for the same `(entity_type, entity_id)` is rejected by `uk_audit_request_pending`.
- [x] After the first row is moved to `APPROVED`/`REJECTED`/`CANCELLED`, a new `PENDING` row for that same target is accepted.
- [x] Any number of `PENDING` `CREATE` rows (with `entity_id IS NULL`) can coexist, and any number of historical rows for one target can coexist.
- [x] All three indexes exist as named above.
- [x] `status` defaults to `PENDING`, and `before_data`/`after_data` accept and return valid JSON.

---
## Execution Result
- Status: DONE
- Files changed: `specs/dba/audit-request.md` (this file — Migration SQL section already contained the final SQL; applied live, no other files touched). No standalone `.sql` file was created anywhere in the repo.
- Notes:
  - Pre-flight passed: `mysql -h 127.0.0.1 -P 3306 -u app -p1234` connects; database `wdd` already existed with the six prerequisite tables (`brand`, `brand_spread`, `currency`, `currency_pair`, `currency_pair_definition`, `spread_group`), confirming V008 was already applied.
  - Applied V009 by writing the migration SQL to a UTF-8 scratch file under the session scratchpad and piping it in with `mysql --default-character-set=utf8mb4 wdd < file.sql` (per the Windows console-codepage gotcha documented in `specs/dba/currency.md`). No non-ASCII SQL was actually needed here, but the same safe pattern was used for consistency. The scratch file was deleted immediately after use; nothing was left in the repo.
  - `SHOW CREATE TABLE audit_request` confirms all 16 columns match the spec exactly (types, nullability, `ENUM` values, `DEFAULT CURRENT_TIMESTAMP`, generated `pending_key` expression), plus `PRIMARY KEY (id)`, `UNIQUE KEY uk_audit_request_pending (pending_key)`, and all three secondary indexes (`idx_audit_request_status_time`, `idx_audit_request_entity`, `idx_audit_request_brand`).
  - Verified **zero foreign keys**: `information_schema.TABLE_CONSTRAINTS` shows only `PRIMARY` and the `UNIQUE` `uk_audit_request_pending` constraint for `audit_request`; `information_schema.KEY_COLUMN_USAGE` with `REFERENCED_TABLE_NAME IS NOT NULL` returns `fk_count = 0`.
  - Verified `pending_key` generation with real inserts: a `PENDING` row with `entity_id=101` produced `pending_key = 'CURRENCY_PAIR:101'`; a `PENDING` `CREATE` row with `entity_id IS NULL` produced `pending_key = NULL`; an `APPROVED` row with `entity_id=102` also produced `pending_key = NULL` (non-`PENDING` status always nulls it regardless of `entity_id`).
  - Verified the one-pending-per-target constraint end-to-end:
    - A second `PENDING` insert for `(CURRENCY_PAIR, 101)` while the first was still `PENDING` failed with `ERROR 1062 (23000) ... Duplicate entry 'CURRENCY_PAIR:101' for key 'audit_request.uk_audit_request_pending'`.
    - After `UPDATE ... SET status='APPROVED' WHERE id=1` (the original pending row for that target), a brand-new `PENDING` row for `(CURRENCY_PAIR, 101)` was accepted and correctly regenerated `pending_key = 'CURRENCY_PAIR:101'`.
  - Verified coexistence: two simultaneous `PENDING` `CREATE` rows (`entity_id IS NULL`, one `CURRENCY_PAIR`, one `BRAND_SPREAD`) inserted without conflict (both `pending_key = NULL`); three historical rows for the same target `(CURRENCY_PAIR, 101)` — `APPROVED`, `REJECTED`, `CANCELLED` — coexisted without any unique-key conflict.
  - Verified `before_data`/`after_data` JSON handling: `JSON_VALID()` returned `1` for both columns, and `JSON_EXTRACT(after_data, '$.spread')` correctly pulled `2.0` / `1.2` from stored JSON snapshots.
  - Cleanup: `DELETE FROM audit_request;` then `SELECT COUNT(*)` confirmed `remaining_rows = 0`. Table structure (columns/indexes/constraints) remains in place — only the test data was removed, as required (no seed data for this table per spec).
