---
status: pending
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
- [ ] `brand` table exists with columns `id, code, name, active, created_at, updated_at` exactly as defined above.
- [ ] `code` has a unique constraint.
- [ ] After migration, `SELECT COUNT(*) FROM brand` returns 7.
- [ ] All seven seeded rows have `active = TRUE` and codes `au, moneta, pug, star, um, vjp, vt`.
