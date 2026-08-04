---
status: done
title: "Audit Module — Generic Approval Request Table"
requirement: "Factor the approval/审核 mechanism out into its own independent audit module, so that any action needing approval can plug into it directly without adding anything to the audit module itself"
---

# Audit Module — Generic Approval Request Table — DBA Spec

## Overview
Create `audit_request`: a single, standalone, entity-agnostic table that is the persistence layer for **the** approval workflow used by this application — submit a proposed create/update/delete, hold it `PENDING`, let a reviewer see the before/after and **approve** (apply it) or **reject** (discard it) it. This is not part of the `currency_pair` feature; it is independent infrastructure that any feature can plug into.

This table was previously specified (and not yet implemented) as `currency_pair_change_request`, then generalized to `change_request` while still living inside an earlier, currency-pair-coupled iteration of this spec. This spec extracts it fully: `audit_request` now has its own identity, independent of any consumer — `currency_pair`'s participation in audit (`specs/backend/currency-pair-approval.md`) requires zero schema of its own.

**Nothing about this table may ever reference a specific consuming entity.** If a future change to this spec adds a `currency_pair_id` column, a `brand_id` column, or anything else named after a specific feature, that change is wrong and belongs in the consumer's own handler/snapshot instead — see "Extensibility" below.

## Requirements
- One table, `audit_request`, holds one row per submitted create/update/delete request, for any entity type in the application that chooses to route its mutations through audit
- `entity_type` identifies which kind of entity a request targets (e.g. `CURRENCY_PAIR`; future consumers add their own value)
- `action_type` distinguishes `CREATE` / `UPDATE` / `DELETE`
- `status` distinguishes `PENDING` (awaiting review) / `APPROVED` / `REJECTED`
- `entity_id` links a request to the target row's id in whatever table `entity_type` maps to (`NULL` for `CREATE`). This column is polymorphic across entity types and therefore carries no foreign key — referential integrity for it is the consuming feature's own application-layer concern
- A full "before" snapshot (pre-change state) and "after" snapshot (proposed state), captured at submission time as a self-contained JSON payload — self-contained specifically so the review screen never needs a live join into whatever table `entity_type` maps to, and stays accurate even if that row later changes or is removed:
  - `CREATE`: before = NULL, after = the proposed new entity's fields
  - `UPDATE`: before = the entity's current fields at submission time, after = the proposed new fields
  - `DELETE`: before = the entity's current fields at submission time, after = NULL
- A short, precomputed `summary` string for list-view rendering without the generic list needing to understand any entity type's field shape
- Track who submitted a request and when, and who reviewed it, when, and (if rejected) why
- Only one `PENDING` request may exist at a time for a given `(entity_type, entity_id)` — enforced by the application layer (`specs/backend/audit.md`), generically, without knowing what `entity_type` means

### Extensibility (the entire point of this table)
Adding a new kind of approval-gated entity — e.g. the previously-discussed future 點差 (rate spread) configuration feature, or anything else — must require **zero changes to this table**: the new feature adds rows with its own `entity_type` value and its own `before_snapshot`/`after_snapshot` shape. `entity_type` is intentionally not constrained by a CHECK-enum for exactly this reason; the set of valid entity types is tracked by the application layer (a handler registry, `specs/backend/audit.md`), not by this schema.

## Table Definition

### `audit_request`

| Column          | Type          | Nullable | Default            | Description                                                                 |
|-----------------|---------------|----------|--------------------|--------------------------------------------------------------------------------|
| id              | BIGINT        | NO       | AUTO_INCREMENT     | Primary key                                                                     |
| entity_type     | VARCHAR(30)   | NO       |                    | Which kind of entity this request targets, e.g. `CURRENCY_PAIR`. Open-ended — validated by the application, not a DB CHECK |
| action_type     | VARCHAR(10)   | NO       |                    | `CREATE`, `UPDATE`, or `DELETE`                                                  |
| entity_id       | BIGINT        | YES      |                    | Id of the target row in whichever table `entity_type` maps to. NULL for `CREATE`. No FK (polymorphic) |
| before_snapshot | JSON          | YES      |                    | Entity-specific field snapshot before the change. NULL for `CREATE`             |
| after_snapshot  | JSON          | YES      |                    | Entity-specific proposed field snapshot. NULL for `DELETE`                      |
| summary         | VARCHAR(255)  | YES      |                    | Precomputed short human-readable label for list rendering                        |
| status          | VARCHAR(10)   | NO       | 'PENDING'          | `PENDING`, `APPROVED`, or `REJECTED`                                             |
| requested_by    | VARCHAR(100)  | YES      |                    | Free-text name of the submitter (no authentication system exists in this app, so this is not a FK to a user table) |
| requested_at    | DATETIME      | NO       | CURRENT_TIMESTAMP  | When the request was submitted                                                  |
| reviewed_by     | VARCHAR(100)  | YES      |                    | Free-text name of the reviewer                                                  |
| reviewed_at     | DATETIME      | YES      |                    | When the request was approved/rejected                                          |
| reject_reason   | VARCHAR(255)  | YES      |                    | Required when rejected; NULL otherwise                                          |
| created_at      | DATETIME      | NO       | CURRENT_TIMESTAMP  | Record creation time                                                             |
| updated_at      | DATETIME      | NO       | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | Last update time                                              |

This spec does not define any entity type's snapshot shape — that belongs to the consumer. For example, `specs/backend/currency-pair-approval.md` documents what `CURRENCY_PAIR`'s `before_snapshot`/`after_snapshot` looks like. `audit_request` itself only knows it stores JSON.

### Indexes / Constraints
- PRIMARY KEY on `id`
- INDEX on `status` (the review queue's default query filters `WHERE status = 'PENDING'`, optionally also `entity_type`)
- INDEX on (`entity_type`, `entity_id`) (dedup checks and "does this row have a pending request" lookups, used generically by any consumer)
- CHECK constraint: `action_type IN ('CREATE', 'UPDATE', 'DELETE')`
- CHECK constraint: `status IN ('PENDING', 'APPROVED', 'REJECTED')`
- No CHECK constraint on `entity_type` (deliberately open-ended — see Extensibility)
- No FK constraints at all on this table — `entity_id` is polymorphic, and the snapshot columns are a point-in-time historical record, not a live reference. No consumer's delete-guard logic should ever be extended to check this table (e.g. currency deletion must remain governed solely by `specs/backend/currency-pair.md`'s existing in-use check against the live `currency_pair` table)
- This is the first table in the codebase to use a `JSON` column type (MySQL 8.0.36 supports it natively) — a deliberate, justified departure from this project's otherwise all-flat-relational-columns convention, made specifically because this table must support arbitrarily many, differently-shaped entity types without a schema change per type. Do not introduce JSON columns elsewhere without similar justification.
- Per-`action_type` shape rules (e.g. `CREATE` → `entity_id`/`before_snapshot` NULL) are enforced by the application layer (`specs/backend/audit.md`), not by a DB CHECK constraint.

## Migration SQL

Next migration after `V004__alter_currency_pair_rate_nullable.sql` (`specs/dba/currency-pair.md`) is `V005__create_audit_request_table.sql`.

```sql
-- V005__create_audit_request_table.sql
-- Creates audit_request: the standalone, entity-agnostic approval-workflow
-- table backing the audit module (specs/backend/audit.md). Any feature that
-- needs create/update/delete to go through review plugs into this same
-- table via its own entity_type value and snapshot shape — no schema change
-- to this table is ever required to add a new consumer.
-- Rollback: DROP TABLE IF EXISTS `audit_request`;

CREATE TABLE IF NOT EXISTS `audit_request` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT,
    `entity_type`     VARCHAR(30)    NOT NULL,
    `action_type`     VARCHAR(10)    NOT NULL,
    `entity_id`       BIGINT         NULL,
    `before_snapshot` JSON           NULL,
    `after_snapshot`  JSON           NULL,
    `summary`         VARCHAR(255)   NULL,
    `status`          VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    `requested_by`    VARCHAR(100)   NULL,
    `requested_at`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `reviewed_by`     VARCHAR(100)   NULL,
    `reviewed_at`     DATETIME       NULL,
    `reject_reason`   VARCHAR(255)   NULL,
    `created_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_audit_request_status` (`status`),
    KEY `idx_audit_request_entity` (`entity_type`, `entity_id`),
    CONSTRAINT `ck_audit_request_action_type` CHECK (`action_type` IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT `ck_audit_request_status` CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

No seed data — this table starts empty; rows are only ever created through the application (`specs/backend/audit.md`).

## Migration Order
1. `V001__create_currency_table.sql` (already applied)
2. `V002__create_brand_table.sql` (already applied)
3. `V003__create_currency_pair_table.sql` (already applied)
4. `V004__alter_currency_pair_rate_nullable.sql` (already applied)
5. `V005__create_audit_request_table.sql` (this spec) — no FK dependency on any other table, so no strict ordering requirement beyond preserving migration numbering

## Acceptance Criteria
- [x] `audit_request` table created with all columns and correct types, including `entity_type`, `entity_id`, `before_snapshot` (JSON), `after_snapshot` (JSON), and `summary`
- [x] CHECK constraints enforce valid `action_type` and `status` enum values; `entity_type` is unconstrained at the DB level
- [x] Indexes exist on `status` and on (`entity_type`, `entity_id`)
- [x] No FK exists anywhere on this table
- [x] A row can be inserted with an arbitrary `entity_type` string (e.g. `'CURRENCY_PAIR'`) and a JSON snapshot, and read back with the JSON content intact
- [x] Timestamps auto-populate on insert and update
- [x] This table's definition contains no column, constraint, or comment naming any specific consumer entity (e.g. no `currency_pair`, `brand_id`, etc.) — confirmed by inspection as the acceptance bar for "genuinely independent"

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/backend/src/main/resources/db/migration/V005__create_audit_request_table.sql` (new)
  - `docker/mysql/initdb/V005__create_audit_request_table.sql` (new, byte-identical to the backend copy — verified via `diff`)
- Notes:
  - Pre-flight passed: connected to MySQL 8.0.36 at `127.0.0.1:3306`, database `wdd`, user `app`; database already existed, no creation needed.
  - Applied `V005__create_audit_request_table.sql` directly against the live `wdd` database via the `mysql` CLI. `SHOW CREATE TABLE audit_request` confirms all columns, types, defaults, the two CHECK constraints, and the two indexes exactly match the spec.
  - Verified zero foreign keys on the table via `information_schema.KEY_COLUMN_USAGE` (count = 0).
  - Verified JSON round-trip: inserted a row with `entity_type='CURRENCY_PAIR'`, `action_type='UPDATE'`, `before_snapshot`/`after_snapshot` JSON objects; read back with JSON content intact.
  - Verified `entity_type` is genuinely unconstrained by inserting a second row with an unrelated, previously-unused value (`entity_type='RATE_SPREAD_CONFIG'`) — accepted with no error, confirming no DB-level enum restricts it to known consumers.
  - Verified CHECK constraints reject invalid enum values: inserting `action_type='BOGUS'` raised `ERROR 3819: Check constraint 'ck_audit_request_action_type' is violated`; inserting `status='BOGUS'` raised the equivalent error for `ck_audit_request_status`.
  - Verified timestamp behavior: `requested_at`/`created_at`/`updated_at` auto-populated to the current UTC-equivalent server time on insert; after an `UPDATE` one second later, `updated_at` advanced past `created_at` while `created_at` stayed fixed.
  - All test/verification rows were deleted after verification, leaving `audit_request` empty (0 rows), matching the spec's "no seed data" requirement.
  - Inspected the final DDL and this migration file: no column, constraint, or comment references `currency_pair`, `brand`, or any other specific consumer entity — the table is fully generic.

### Increment 1 — 2026-08-03
- Status: DONE
- Change: retired the `docker/mysql/initdb/` mechanism project-wide — removed its volume mount from `docker/docker-compose.yml`, deleted the `docker/mysql/initdb/` directory (all `V001`–`V011` files), and updated `.claude/agents/dba.md`/`.claude/commands/dev.md` so migration SQL now lives only inside each spec's `## Migration SQL` section and is applied directly against the live database when `/dev` runs — no standalone `.sql` artifact is ever written. No schema or data change; `V005` (already applied) is unaffected.

### Teardown — 2026-08-03
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.

### Increment 2 — 2026-08-03
- Status: DONE
- Files changed: none (no standalone `.sql` file written anywhere; migration SQL lives only in this spec's `## Migration SQL` section per current convention)
- Notes:
  - Pre-flight passed: connected to MySQL 8.0.36 at `127.0.0.1:3306`, database `wdd`, user `app`; database already existed (created previously), no creation needed.
  - Confirmed pre-existing tables `currency`, `brand`, `currency_pair` already present and `audit_request` absent, matching the from-scratch-rebuild sequencing (`V001`–`V004` already applied; `V005` next).
  - Applied `V005__create_audit_request_table.sql` directly against the live `wdd` database via the `mysql` CLI (`CREATE TABLE IF NOT EXISTS audit_request ...` exactly as specified in `## Migration SQL`).
  - `SHOW CREATE TABLE audit_request` confirms all columns, types, defaults, the two CHECK constraints (`ck_audit_request_action_type`, `ck_audit_request_status`), and the two indexes (`idx_audit_request_status`, `idx_audit_request_entity`) exactly match the spec.
  - Verified zero foreign keys on the table via `information_schema.KEY_COLUMN_USAGE` (count = 0).
  - Verified JSON round-trip: inserted a row with `entity_type='CURRENCY_PAIR'`, `action_type='UPDATE'`, `before_snapshot`/`after_snapshot` JSON objects; read back with JSON content intact (`{"rate": 1.1}` / `{"rate": 1.2}`).
  - Verified `entity_type` is genuinely unconstrained by inserting a second row with an unrelated value (`entity_type='RATE_SPREAD_CONFIG'`) — accepted with no error, confirming no DB-level enum restricts it to known consumers.
  - Verified CHECK constraints reject invalid enum values: `action_type='BOGUS'` raised `ERROR 3819: Check constraint 'ck_audit_request_action_type' is violated`; `status='BOGUS'` raised the equivalent error for `ck_audit_request_status`.
  - Verified timestamp behavior: `requested_at`/`created_at`/`updated_at` auto-populated on insert; after an `UPDATE` one second later, `updated_at` advanced (04:24:15 → 04:24:22) while `created_at` stayed fixed at 04:24:15.
  - All test/verification rows were deleted after verification, leaving `audit_request` empty (0 rows), matching the spec's "no seed data" requirement.
  - Inspected the final DDL and this spec's Migration SQL: no column, constraint, or comment references `currency_pair`, `brand`, or any other specific consumer entity — the table remains fully generic.

### Teardown — 2026-08-04
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.

### Increment 3 — 2026-08-04
- Status: DONE
- Files changed: none (no standalone `.sql` file written anywhere; migration SQL lives only in this spec's `## Migration SQL` section per current convention)
- Notes:
  - Pre-flight passed: read `env.md` (Engine: MySQL 8.0.36, Host: 127.0.0.1, Port: 3306, Database: wdd, Username: app, Password: 1234); connected via the `mysql` CLI (`SELECT 1;` succeeded); database `wdd` already existed, no creation needed.
  - Confirmed pre-existing tables `currency`, `brand`, `currency_pair` already present and `audit_request` absent, matching the from-scratch-rebuild sequencing (`V001`–`V004` already applied; `V005` next).
  - Applied `V005__create_audit_request_table.sql` directly against the live `wdd` database via the `mysql` CLI (`CREATE TABLE IF NOT EXISTS audit_request ...` exactly as specified in `## Migration SQL`).
  - `SHOW CREATE TABLE audit_request` confirms all columns, types, defaults, the two CHECK constraints (`ck_audit_request_action_type`, `ck_audit_request_status`), and the two indexes (`idx_audit_request_status`, `idx_audit_request_entity`) exactly match the spec.
  - Verified zero foreign keys on the table via `information_schema.KEY_COLUMN_USAGE` (count = 0).
  - Verified JSON round-trip: inserted a row with `entity_type='CURRENCY_PAIR'`, `action_type='UPDATE'`, `before_snapshot`/`after_snapshot` JSON objects; read back with JSON content intact (`{"rate": 1.1}` / `{"rate": 1.2}`).
  - Verified `entity_type` is genuinely unconstrained by inserting a second row with an unrelated value (`entity_type='RATE_SPREAD_CONFIG'`) — accepted with no error, confirming no DB-level enum restricts it to known consumers.
  - Verified CHECK constraints reject invalid enum values: `action_type='BOGUS'` raised `ERROR 3819: Check constraint 'ck_audit_request_action_type' is violated`; `status='BOGUS'` raised the equivalent error for `ck_audit_request_status`.
  - Verified timestamp behavior: `requested_at`/`created_at`/`updated_at` auto-populated on insert (06:28:27); after an `UPDATE` ten seconds later, `updated_at` advanced to 06:28:37 while `created_at` stayed fixed at 06:28:27.
  - All test/verification rows were deleted after verification, leaving `audit_request` empty (0 rows), matching the spec's "no seed data" requirement.
  - Inspected the final DDL and this spec's Migration SQL: no column, constraint, or comment references `currency_pair`, `brand`, or any other specific consumer entity — the table remains fully generic.
  - Re-checked all Acceptance Criteria boxes and set frontmatter `status: done`.
