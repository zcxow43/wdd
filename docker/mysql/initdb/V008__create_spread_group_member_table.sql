-- V008__create_spread_group_member_table.sql
-- Creates spread_group_member: assigns currency pairs into a spread_group.
-- The UNIQUE key on currency_pair_id enforces "a currency pair belongs to
-- at most one spread group" at the database level.
-- Rollback: DROP TABLE IF EXISTS `spread_group_member`;

CREATE TABLE IF NOT EXISTS `spread_group_member` (
    `id`                BIGINT    NOT NULL AUTO_INCREMENT,
    `spread_group_id`   BIGINT    NOT NULL,
    `currency_pair_id`  BIGINT    NOT NULL,
    `created_at`        DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spread_group_member_currency_pair` (`currency_pair_id`),
    KEY `idx_spread_group_member_group` (`spread_group_id`),
    CONSTRAINT `fk_spread_group_member_group` FOREIGN KEY (`spread_group_id`) REFERENCES `spread_group` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `fk_spread_group_member_pair` FOREIGN KEY (`currency_pair_id`) REFERENCES `currency_pair` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
