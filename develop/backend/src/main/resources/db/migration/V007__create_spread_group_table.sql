-- V007__create_spread_group_table.sql
-- Creates spread_group: brand-scoped, freely CRUD-able custom spread groups.
-- Rollback: DROP TABLE IF EXISTS `spread_group`;

CREATE TABLE IF NOT EXISTS `spread_group` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT,
    `brand_id`        BIGINT         NOT NULL,
    `name`            VARCHAR(100)   NOT NULL,
    `deposit_spread`  DECIMAL(18,8)  NOT NULL,
    `withdraw_spread` DECIMAL(18,8)  NOT NULL,
    `created_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spread_group_brand_name` (`brand_id`, `name`),
    CONSTRAINT `ck_spread_group_deposit_nonneg` CHECK (`deposit_spread` >= 0),
    CONSTRAINT `ck_spread_group_withdraw_nonneg` CHECK (`withdraw_spread` >= 0),
    CONSTRAINT `fk_spread_group_brand` FOREIGN KEY (`brand_id`) REFERENCES `brand` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
