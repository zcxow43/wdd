-- V001__create_currency_table.sql
-- Creates the currency reference table and seeds it with common currencies.
-- Rollback: DROP TABLE IF EXISTS `currency`;

CREATE TABLE IF NOT EXISTS `currency` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `code`           VARCHAR(3)    NOT NULL,
    `name`           VARCHAR(100)  NOT NULL,
    `name_zh`        VARCHAR(100)  NULL,
    `symbol`         VARCHAR(10)   NULL,
    `decimal_places` INT           NOT NULL DEFAULT 2,
    `active`         TINYINT(1)    NOT NULL DEFAULT 1,
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_currency_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
