---
status: pending
title: "Currency Table"
requirement: "Create currency table with seed data for the currency API"
---

# Currency Table — DBA Spec

## Overview
Create the currency reference table to store currency codes, names, and metadata. Populate with seed data for common currencies.

## Requirements
- Single table `currency` to hold all supported currencies
- Seed with at least 10 common currencies including TWD, USD, EUR, JPY, GBP, CNY, HKD, SGD, AUD, CAD
- Support soft-delete via `active` flag

## Table Definition

### `currency`

| Column       | Type           | Nullable | Default            | Description              |
|--------------|----------------|----------|--------------------|--------------------------|
| id           | BIGINT         | NO       | AUTO_INCREMENT     | Primary key              |
| code         | VARCHAR(3)     | NO       |                    | ISO 4217 currency code   |
| name         | VARCHAR(100)   | NO       |                    | Currency English name     |
| name_zh      | VARCHAR(100)   | YES      |                    | Currency Chinese name     |
| symbol       | VARCHAR(10)    | YES      |                    | Currency symbol (e.g. $)  |
| decimal_places | INT          | NO       | 2                  | Number of decimal places |
| active       | TINYINT(1)     | NO       | 1                  | 1=active, 0=inactive     |
| created_at   | DATETIME       | NO       | CURRENT_TIMESTAMP  | Record creation time     |
| updated_at   | DATETIME       | NO       | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | Last update time |

### Indexes
- PRIMARY KEY on `id`
- UNIQUE index on `code`

## Migration SQL

```sql
CREATE TABLE IF NOT EXISTS `currency` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `code`           VARCHAR(3)    NOT NULL,
    `name`           VARCHAR(100)  NOT NULL,
    `name_zh`        VARCHAR(100)  NULL,
    `symbol`         VARCHAR(10)   NULL,
    `decimal_places`  INT          NOT NULL DEFAULT 2,
    `active`         TINYINT(1)    NOT NULL DEFAULT 1,
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_currency_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Seed Data

```sql
INSERT INTO `currency` (`code`, `name`, `name_zh`, `symbol`, `decimal_places`, `active`) VALUES
('TWD', 'New Taiwan Dollar',       '新台幣',     'NT$', 0, 1),
('USD', 'United States Dollar',    '美元',       '$',   2, 1),
('EUR', 'Euro',                    '歐元',       '€',   2, 1),
('JPY', 'Japanese Yen',            '日圓',       '¥',   0, 1),
('GBP', 'British Pound Sterling',  '英鎊',       '£',   2, 1),
('CNY', 'Chinese Yuan',            '人民幣',     '¥',   2, 1),
('HKD', 'Hong Kong Dollar',        '港幣',       'HK$', 2, 1),
('SGD', 'Singapore Dollar',        '新加坡幣',   'S$',  2, 1),
('AUD', 'Australian Dollar',       '澳幣',       'A$',  2, 1),
('CAD', 'Canadian Dollar',         '加幣',       'C$',  2, 1);
```

## Acceptance Criteria
- [ ] `currency` table created with all columns and correct types
- [ ] Unique constraint on `code` column
- [ ] 10 seed records inserted successfully
- [ ] `active` defaults to 1, `decimal_places` defaults to 2
- [ ] Timestamps auto-populate on insert and update
