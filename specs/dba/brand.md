---
status: done
title: "Brand Master Table"
requirement: "匯率中心需要品牌主檔，內建七個品牌 au, moneta, pug, star, um, vjp, vt，品牌可以開啟關閉"
---

# Brand — DBA Spec

## Overview
`brand` is the ownership root for every brand-scoped configuration in the exchange rate center. This spec covers the `brand` table itself and its seed data — seven brands, each independently toggleable via `active`.

## Requirements
- One table: `brand`.
- Seed exactly seven rows on creation: `au`, `moneta`, `pug`, `star`, `um`, `vjp`, `vt` — all `active = true` by default.
- `code` and `name` are set once at seed time and never changed by the application — only `active` is ever mutated.

## Implementation Details

### Table: `brand`

| Column | Type | Constraint |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| code | VARCHAR(32) | NOT NULL, UNIQUE |
| name | VARCHAR(64) | NOT NULL |
| active | BOOLEAN | NOT NULL, DEFAULT TRUE |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

## Migration SQL — V001__create_brand.sql

```sql
CREATE TABLE brand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_brand_code UNIQUE (code)
);

INSERT INTO brand (code, name, active) VALUES
    ('au', 'au', TRUE),
    ('moneta', 'moneta', TRUE),
    ('pug', 'pug', TRUE),
    ('star', 'star', TRUE),
    ('um', 'um', TRUE),
    ('vjp', 'vjp', TRUE),
    ('vt', 'vt', TRUE);
```

## Acceptance Criteria
- [x] `brand` table exists with columns `id, code, name, active, created_at, updated_at` exactly as defined above.
- [x] `code` has a unique constraint.
- [x] After migration, `SELECT COUNT(*) FROM brand` returns 7.
- [x] All seven seeded rows have `active = TRUE` and codes `au, moneta, pug, star, um, vjp, vt`.

---
## Execution Result
- Status: DONE
- Files changed: `specs/dba/brand.md` (this spec, checked off acceptance criteria and status). No standalone `.sql` files created — migration SQL applied directly against the live MySQL database at `127.0.0.1:3306`, database `wdd`, via the `mysql` CLI.
- Notes: Ran pre-flight validation (env.md fields present, connection succeeded, database `wdd` already existed, target table `brand` did not exist). Executed `V001__create_brand.sql` from the Migration SQL section above directly against the live database: created the `brand` table with `id, code, name, active, created_at, updated_at` and unique constraint `uk_brand_code` on `code`, then seeded the 7 rows (`au, moneta, pug, star, um, vjp, vt`), all with `active = TRUE`. Verified via `DESCRIBE brand`, `SHOW INDEX FROM brand`, `SELECT COUNT(*) FROM brand` (returned 7), and `SELECT code, name, active FROM brand` (all 7 codes present, all `active = 1`).
