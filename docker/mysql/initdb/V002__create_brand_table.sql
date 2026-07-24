-- V002__create_brand_table.sql
-- Creates the brand reference table and seeds it with the fixed set of brands.
-- Rollback: DROP TABLE IF EXISTS `brand`;

CREATE TABLE IF NOT EXISTS `brand` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `code`        VARCHAR(20)   NOT NULL,
    `name`        VARCHAR(100)  NOT NULL,
    `active`      TINYINT(1)    NOT NULL DEFAULT 1,
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_brand_code` (`code`),
    CONSTRAINT `ck_brand_code_uppercase` CHECK (BINARY `code` = BINARY UPPER(`code`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `brand` (`code`, `name`, `active`) VALUES
('AU',     'AU',     1),
('MONETA', 'MONETA', 1),
('PUG',    'PUG',    1),
('STAR',   'STAR',   1),
('UM',     'UM',     1),
('VJP',    'VJP',    1),
('VT',     'VT',     1);
