-- V003__create_currency_pair_table.sql
-- Creates the currency_pair table (base -> quote exchange rate, scoped per brand)
-- and seeds it with sample pairs across brands.
-- Rollback: DROP TABLE IF EXISTS `currency_pair`;

CREATE TABLE IF NOT EXISTS `currency_pair` (
    `id`                 BIGINT         NOT NULL AUTO_INCREMENT,
    `brand_id`           BIGINT         NOT NULL,
    `base_currency_id`   BIGINT         NOT NULL,
    `quote_currency_id`  BIGINT         NOT NULL,
    `rate`               DECIMAL(18,8)  NOT NULL,
    `rate_type`          VARCHAR(10)    NOT NULL DEFAULT 'MANUAL',
    `active`             TINYINT(1)     NOT NULL DEFAULT 1,
    `created_at`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_currency_pair_brand_base_quote` (`brand_id`, `base_currency_id`, `quote_currency_id`),
    CONSTRAINT `ck_currency_pair_distinct` CHECK (`base_currency_id` <> `quote_currency_id`),
    CONSTRAINT `ck_currency_pair_rate_type` CHECK (`rate_type` IN ('MANUAL', 'AUTO')),
    CONSTRAINT `ck_currency_pair_rate_positive` CHECK (`rate` > 0),
    CONSTRAINT `fk_currency_pair_brand` FOREIGN KEY (`brand_id`) REFERENCES `brand` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_currency_pair_base` FOREIGN KEY (`base_currency_id`) REFERENCES `currency` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_currency_pair_quote` FOREIGN KEY (`quote_currency_id`) REFERENCES `currency` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `currency_pair` (`brand_id`, `base_currency_id`, `quote_currency_id`, `rate`, `rate_type`, `active`)
SELECT br.id, b.id, q.id, v.rate, v.rate_type, 1
FROM (
    SELECT 'AU'     AS brand_code, 'USD' AS base_code, 'TWD' AS quote_code, 32.50000000 AS rate, 'MANUAL' AS rate_type
    UNION ALL SELECT 'AU',     'EUR', 'TWD', 35.20000000, 'MANUAL'
    UNION ALL SELECT 'MONETA', 'USD', 'JPY', 157.30000000, 'AUTO'
    UNION ALL SELECT 'VT',     'USD', 'EUR', 0.92000000, 'AUTO'
) v
JOIN `brand` br ON br.code = v.brand_code
JOIN `currency` b ON b.code = v.base_code
JOIN `currency` q ON q.code = v.quote_code;
