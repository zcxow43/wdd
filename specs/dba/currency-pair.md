---
status: done
title: "Currency Pair Table (Brand-Scoped)"
requirement: "新增品牌幣種對：每個品牌各自的幣種對設定（自動/手動匯率、開啟關閉），幣種對新增時自動為所有品牌建立一筆，預設關閉且為自動匯率"
---

# Currency Pair — DBA Spec

## Overview
`currency_pair` is each brand's own settings for a given currency pair definition — whether it's enabled, and whether its rate is automatic or a manually-entered value. One row per `(currency_pair_definition, brand)` combination. Rows are primarily created by the fan-out in [currency-pair-definition.md](../backend/currency-pair-definition.md) (one per existing brand whenever a definition is created), but the API also supports creating/deleting individual rows directly (see [currency-pair.md](../backend/currency-pair.md)).

## Requirements
- One table: `currency_pair`.
- Exactly one row per `(currency_pair_definition_id, brand_id)` pair — enforced by a unique constraint.
- `rate_type` is `AUTO` or `MANUAL`; `rate` pairs with it (validated at the application layer, not by a DB CHECK, matching this project's existing convention of keeping conditional field validation in the service layer).
- `active` defaults to `false` — a newly created row (whether via fan-out or direct creation) starts disabled.
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

## Acceptance Criteria
- [x] `currency_pair` table exists with columns exactly as defined above.
- [x] Unique constraint on `(currency_pair_definition_id, brand_id)`.
- [x] `currency_pair_definition_id` foreign key has `ON DELETE CASCADE`.
- [x] `brand_id` foreign keys to `brand.id`.
- [x] `rate_type` defaults to `AUTO`, `active` defaults to `false`.

---
## Execution Result
- Status: DONE
- Files changed: `specs/dba/currency-pair.md` (this file — migration SQL executed live, frontmatter status updated, acceptance criteria checked off)
- Notes: Pre-flight validated `env.md` (MySQL 8.0.36, 127.0.0.1:3306, db `wdd`, user `app`) and confirmed connectivity plus existing `wdd` database. Confirmed prerequisite tables `currency_pair_definition` (V004) and `brand` already present. Executed V005 DDL directly via `mysql` CLI against the live `wdd` database — no standalone `.sql` file was created. Verified via `DESCRIBE`/`SHOW CREATE TABLE` that the table matches the spec exactly: PK `id`, unique key `uk_currency_pair` on `(currency_pair_definition_id, brand_id)`, `fk_currency_pair_definition` with `ON DELETE CASCADE` to `currency_pair_definition(id)`, `fk_currency_pair_brand` to `brand(id)`, `rate_type` ENUM default `'AUTO'`, `active` default `0` (false). No application code changes were made — this spec is DBA-only (table creation); the fan-out on definition create and the AUTO/MANUAL+rate validation are handled by the corresponding backend spec, not here.
