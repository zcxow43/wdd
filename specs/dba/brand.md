---
status: done
title: "Brand Table"
requirement: "Create brand table (fixed set: AU, MONETA, PUG, STAR, UM, VJP, VT — uppercase codes), toggleable active flag; each brand owns its own currency pairs"
---

# Brand Table — DBA Spec

## Overview
Create the `brand` table to hold the fixed set of brands that currency pairs are scoped under (see `specs/dba/currency-pair.md`, which adds a `brand_id` FK to `currency_pair` in migration `V003`). Brands are a small, fixed, seeded list — there is no requirement to create or delete brands at runtime, only to enable/disable them.

## Requirements
- New table `brand` holding exactly the 7 seeded brands: `AU`, `MONETA`, `PUG`, `STAR`, `UM`, `VJP`, `VT`
- `code` must always be uppercase and unique
- `active` flag supports enabling/disabling a brand
- `currency_pair` rows reference `brand.id`; deleting a brand referenced by any currency pair must be rejected at the database level (see `specs/dba/currency-pair.md`)

## Table Definition

### `brand`

| Column       | Type           | Nullable | Default            | Description                          |
|--------------|----------------|----------|--------------------|----------------------------------------|
| id           | BIGINT         | NO       | AUTO_INCREMENT     | Primary key                            |
| code         | VARCHAR(20)    | NO       |                    | Brand code, uppercase, e.g. `AU`, `MONETA` |
| name         | VARCHAR(100)   | NO       |                    | Display name (defaults to `code` at seed time) |
| active       | TINYINT(1)     | NO       | 1                  | 1=enabled, 0=disabled                  |
| created_at   | DATETIME       | NO       | CURRENT_TIMESTAMP  | Record creation time                   |
| updated_at   | DATETIME       | NO       | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | Last update time  |

### Indexes / Constraints
- PRIMARY KEY on `id`
- UNIQUE index on `code`
- CHECK constraint: `code = UPPER(code)`

## Migration SQL

Next migration in sequence after `V001__create_currency_table.sql` is `V002__create_brand_table.sql`.

```sql
CREATE TABLE IF NOT EXISTS `brand` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `code`        VARCHAR(20)   NOT NULL,
    `name`        VARCHAR(100)  NOT NULL,
    `active`      TINYINT(1)    NOT NULL DEFAULT 1,
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_brand_code` (`code`),
    CONSTRAINT `ck_brand_code_uppercase` CHECK (`code` = UPPER(`code`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Seed Data

```sql
INSERT INTO `brand` (`code`, `name`, `active`) VALUES
('AU',     'AU',     1),
('MONETA', 'MONETA', 1),
('PUG',    'PUG',    1),
('STAR',   'STAR',   1),
('UM',     'UM',     1),
('VJP',    'VJP',    1),
('VT',     'VT',     1);
```

## Migration Order
1. `V001__create_currency_table.sql` (already applied)
2. `V002__create_brand_table.sql` (this spec)
3. `V003__create_currency_pair_table.sql` (`specs/dba/currency-pair.md`) — must run after this migration since it FKs to `brand`

## Acceptance Criteria
- [x] `brand` table created with all columns and correct types
- [x] Unique constraint on `code`
- [x] CHECK constraint enforces `code` is uppercase
- [x] 7 seed rows inserted: AU, MONETA, PUG, STAR, UM, VJP, VT, all `active = 1`
- [x] Timestamps auto-populate on insert and update

---
## Execution Result
- Status: DONE
- Files changed:
  - develop/backend/src/main/resources/db/migration/V002__create_brand_table.sql (new)
  - docker/mysql/initdb/V002__create_brand_table.sql (new)
- Notes: Ran DBA pre-flight (env.md validated, connectivity to 127.0.0.1:3306 confirmed via `mysql -h 127.0.0.1 -P 3306 -u app -p1234 -e "SELECT 1;"`, database `wdd` already existed with only the `currency` table present, confirming this is migration V002). Created migration V002 defining the `brand` table (PK on id, UNIQUE key on code, CHECK constraint enforcing uppercase code, active defaults to 1, created_at/updated_at auto-managed) plus the 7 seed rows (AU, MONETA, PUG, STAR, UM, VJP, VT).

  Applied the migration directly against the live `wdd` database via `mysql ... < V002__create_brand_table.sql`. During verification I discovered the CHECK constraint as originally specified (`code = UPPER(code)`) was **not actually enforced**: because the table uses the case-insensitive collation `utf8mb4_unicode_ci`, MySQL evaluated `'xx' = UPPER('xx')` as TRUE (case-insensitive string comparison), so a lowercase insert (`'xx'`) silently succeeded despite the CHECK constraint being present. I fixed this by rewriting the constraint to force a binary (case-sensitive) comparison: `CHECK (BINARY code = BINARY UPPER(code))`. I applied this fix live via `ALTER TABLE brand DROP CHECK ck_brand_code_uppercase` + `ALTER TABLE brand ADD CONSTRAINT ck_brand_code_uppercase CHECK (BINARY code = BINARY UPPER(code))`, re-tested (lowercase insert now correctly rejected with error 3819, uppercase insert succeeds), cleaned up test rows, and updated both migration files (backend and docker init) to use the corrected `BINARY` comparison so future fresh deployments get the working constraint from the start.

  Final verification via `SHOW TABLES` (brand + currency present), `DESCRIBE brand` (all 6 columns with correct types/nullability/defaults), `SHOW INDEX FROM brand` (PRIMARY on id, uk_brand_code UNIQUE on code), `SHOW CREATE TABLE brand` (confirms `CHECK ((cast(code as char charset binary) = cast(upper(code) as char charset binary)))`), and `SELECT COUNT(*) FROM brand` / `SELECT * FROM brand` (exactly 7 rows: AU, MONETA, PUG, STAR, UM, VJP, VT, ids 1-7, all `active = 1`). Note: the AUTO_INCREMENT counter is now at 10 due to the two temporary test-constraint rows inserted and deleted during verification (ids 8, 9); this has no functional impact since the 7 seed rows retain ids 1-7 as expected.
