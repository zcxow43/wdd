-- V009__create_currency_pair_definition_table.sql
-- Creates currency_pair_definition: a brand-agnostic master record for a
-- (base, quote) direction, guarding against its reverse direction ever
-- being created, plus forward/reverse rate-display precision. Purely
-- additive — no change to currency_pair/brand/currency. The relationship to
-- currency_pair rows provisioned from a definition (specs/backend/
-- currency-pair-definition.md) is implicit (matching currency ids), not an FK.
-- Rollback: DROP TABLE IF EXISTS `currency_pair_definition`;

CREATE TABLE IF NOT EXISTS `currency_pair_definition` (
    `id`                BIGINT    NOT NULL AUTO_INCREMENT,
    `base_currency_id`  BIGINT    NOT NULL,
    `quote_currency_id` BIGINT    NOT NULL,
    `forward_precision` TINYINT   NOT NULL,
    `reverse_precision` TINYINT   NOT NULL,
    `pair_key_low`  BIGINT AS (LEAST(`base_currency_id`, `quote_currency_id`)) STORED,
    `pair_key_high` BIGINT AS (GREATEST(`base_currency_id`, `quote_currency_id`)) STORED,
    `created_at`        DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_currency_pair_definition_pair_key` (`pair_key_low`, `pair_key_high`),
    CONSTRAINT `ck_currency_pair_definition_distinct` CHECK (`base_currency_id` <> `quote_currency_id`),
    CONSTRAINT `ck_currency_pair_definition_forward_precision` CHECK (`forward_precision` BETWEEN 0 AND 8),
    CONSTRAINT `ck_currency_pair_definition_reverse_precision` CHECK (`reverse_precision` BETWEEN 0 AND 8),
    CONSTRAINT `fk_currency_pair_definition_base` FOREIGN KEY (`base_currency_id`) REFERENCES `currency` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_currency_pair_definition_quote` FOREIGN KEY (`quote_currency_id`) REFERENCES `currency` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
