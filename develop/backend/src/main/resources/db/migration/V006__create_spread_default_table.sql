-- V006__create_spread_default_table.sql
-- Creates spread_default: one default deposit/withdraw spread row per brand,
-- used whenever a currency pair is not assigned to a custom spread_group.
-- Rollback: DROP TABLE IF EXISTS `spread_default`;

CREATE TABLE IF NOT EXISTS `spread_default` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT,
    `brand_id`        BIGINT         NOT NULL,
    `deposit_spread`  DECIMAL(18,8)  NOT NULL DEFAULT 0,
    `withdraw_spread` DECIMAL(18,8)  NOT NULL DEFAULT 0,
    `created_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spread_default_brand` (`brand_id`),
    CONSTRAINT `ck_spread_default_deposit_nonneg` CHECK (`deposit_spread` >= 0),
    CONSTRAINT `ck_spread_default_withdraw_nonneg` CHECK (`withdraw_spread` >= 0),
    CONSTRAINT `fk_spread_default_brand` FOREIGN KEY (`brand_id`) REFERENCES `brand` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `spread_default` (`brand_id`, `deposit_spread`, `withdraw_spread`)
SELECT `id`, 0, 0 FROM `brand`;
