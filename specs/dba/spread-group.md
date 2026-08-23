---
status: done
title: "Spread Group Table"
requirement: "點差分為預設點差與群組點差；群組可以拉品牌幣種對進行設定，每個品牌幣種對只能加入一個群組"
---

# Spread Group — DBA Spec

## Overview
`spread_group` holds a brand's named **spread groups** (點差群組) — each with its own deposit spread (入金點差) and withdrawal spread (出金點差) that override the brand's default from [brand-spread.md](brand-spread.md) for the brand currency pairs assigned to it. A group belongs to exactly one brand. Membership itself is **not** stored here: it is the nullable `currency_pair.spread_group_id` column defined in [currency-pair.md](currency-pair.md), which is what structurally guarantees "每個品牌幣種對只能加入一個群組" — a pair has at most one group because it has at most one value in that single column.

## Requirements
- One table: `spread_group`.
- A group is brand-scoped: `brand_id` is required and immutable in practice (the API never reassigns a group to another brand).
- `name` is unique per brand — two brands may both have a group named `VIP`, but one brand cannot have two.
- Both spreads are non-negative decimals with up to 8 decimal places, same style as `brand_spread` and `currency_pair.rate`.
- Both spreads default to `0`.
- Deleting a brand cascades to delete its groups.
- Deleting a group must **not** delete its member currency pairs — the members simply revert to the brand's default spread. That behavior is implemented by the `ON DELETE SET NULL` on `currency_pair.spread_group_id` (see [currency-pair.md](currency-pair.md)'s V008 migration).

## Implementation Details

### Table: `spread_group`

| Column | Type | Constraint |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| brand_id | BIGINT | NOT NULL, FK → `brand.id`, ON DELETE CASCADE |
| name | VARCHAR(50) | NOT NULL |
| deposit_spread | DECIMAL(18,8) | NOT NULL, DEFAULT 0, CHECK >= 0 |
| withdrawal_spread | DECIMAL(18,8) | NOT NULL, DEFAULT 0, CHECK >= 0 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

Unique constraint: `uk_spread_group_brand_name` on `(brand_id, name)`.

## Migration SQL — V007__create_spread_group.sql

Comes after `V006__create_brand_spread.sql` (`specs/dba/brand-spread.md`). Must run after `V001__create_brand.sql` since this table FKs to `brand`, and before `V008` (`specs/dba/currency-pair.md`), which adds the FK pointing at this table.

```sql
CREATE TABLE spread_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    deposit_spread DECIMAL(18,8) NOT NULL DEFAULT 0,
    withdrawal_spread DECIMAL(18,8) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_spread_group_brand_name UNIQUE (brand_id, name),
    CONSTRAINT fk_spread_group_brand FOREIGN KEY (brand_id) REFERENCES brand(id) ON DELETE CASCADE,
    CONSTRAINT ck_spread_group_deposit CHECK (deposit_spread >= 0),
    CONSTRAINT ck_spread_group_withdrawal CHECK (withdrawal_spread >= 0)
);
```

No seed data — groups are created entirely by the user.

## Acceptance Criteria
- [x] `spread_group` table exists with columns exactly as defined above.
- [x] Unique constraint on `(brand_id, name)`; the same name under two different brands is accepted, a duplicate under one brand is rejected.
- [x] `brand_id` foreign key to `brand.id` with `ON DELETE CASCADE`.
- [x] Both spread columns default to `0` and reject negative values (CHECK enforced).
- [x] Table starts empty (no seed rows).

---
## Execution Result
- Status: DONE
- Files changed: `specs/dba/spread-group.md` (this file — migration SQL section already contained final SQL, applied as-is; no code files elsewhere per DBA rules).
- Applied: `V007__create_spread_group.sql` executed against live MySQL (127.0.0.1:3306, db `wdd`, user `app`) via `mysql --default-character-set=utf8mb4 wdd < scratch.sql`. No errors.
- Verified schema: `SHOW CREATE TABLE spread_group` / `DESCRIBE spread_group` match the spec exactly — `id` BIGINT PK AUTO_INCREMENT, `brand_id` BIGINT NOT NULL with `fk_spread_group_brand` → `brand(id) ON DELETE CASCADE`, `name` VARCHAR(50) NOT NULL, `deposit_spread`/`withdrawal_spread` DECIMAL(18,8) NOT NULL DEFAULT 0 with `ck_spread_group_deposit`/`ck_spread_group_withdrawal` CHECK (>= 0), `created_at`/`updated_at` TIMESTAMP with expected defaults, unique key `uk_spread_group_brand_name` on `(brand_id, name)`.
- Verified constraint behavior (all test rows subsequently deleted):
  - Inserted `name='VIP'` under `brand_id=1` and `brand_id=2` — both succeeded (same name across different brands is allowed).
  - Inserted `name='VIP'` again under `brand_id=1` — rejected with `ERROR 1062 Duplicate entry '1-VIP' for key 'spread_group.uk_spread_group_brand_name'`.
  - Inserted `deposit_spread=-1` — rejected with `ERROR 3819 Check constraint 'ck_spread_group_deposit' is violated.`
  - Inserted `withdrawal_spread=-1` — rejected with `ERROR 3819 Check constraint 'ck_spread_group_withdrawal' is violated.`
  - Created a temporary brand, inserted a `spread_group` row referencing it (confirmed present via `COUNT(*)=1`), deleted the brand, confirmed the `spread_group` row was cascade-deleted (`COUNT(*)=0` for that `brand_id`).
- Cleanup: all test rows removed; `spread_group` confirmed empty (`SELECT COUNT(*)` = 0) and `brand` table confirmed back to its original 7 rows with the temporary test brand fully gone.
