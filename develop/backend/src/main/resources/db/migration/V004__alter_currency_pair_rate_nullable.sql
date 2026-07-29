-- V004__alter_currency_pair_rate_nullable.sql
-- Makes currency_pair.rate nullable; clears rate for existing AUTO rows;
-- replaces the unconditional rate>0 CHECK with a per-rate_type rule;
-- adds more seed/test data covering all 7 brands.
-- Rollback: not straightforwardly reversible (would require re-populating
-- cleared AUTO rates); restore from backup if needed.

-- 1. Make rate nullable first (required before we can set it to NULL).
ALTER TABLE `currency_pair` MODIFY COLUMN `rate` DECIMAL(18,8) NULL;

-- 2. Clear rate for any existing AUTO rows so they satisfy the new CHECK below.
UPDATE `currency_pair` SET `rate` = NULL WHERE `rate_type` = 'AUTO';

-- 3. Replace the unconditional rate>0 CHECK with a per-rate_type rule.
ALTER TABLE `currency_pair` DROP CONSTRAINT `ck_currency_pair_rate_positive`;
ALTER TABLE `currency_pair` ADD CONSTRAINT `ck_currency_pair_rate_valid` CHECK (
    (`rate_type` = 'MANUAL' AND `rate` IS NOT NULL AND `rate` > 0)
    OR
    (`rate_type` = 'AUTO' AND `rate` IS NULL)
);

-- 4. More seed/test data: cover the remaining brands (PUG, STAR, UM, VJP)
--    with a mix of MANUAL and AUTO pairs.
INSERT INTO `currency_pair` (`brand_id`, `base_currency_id`, `quote_currency_id`, `rate`, `rate_type`, `active`)
SELECT br.id, b.id, q.id, v.rate, v.rate_type, 1
FROM (
    SELECT 'PUG'   AS brand_code, 'USD' AS base_code, 'TWD' AS quote_code, 31.80000000 AS rate, 'MANUAL' AS rate_type
    UNION ALL SELECT 'PUG',   'EUR', 'USD', NULL,          'AUTO'
    UNION ALL SELECT 'STAR',  'USD', 'HKD', 7.82000000,    'MANUAL'
    UNION ALL SELECT 'STAR',  'GBP', 'USD', NULL,          'AUTO'
    UNION ALL SELECT 'UM',    'USD', 'CNY', 7.10000000,    'MANUAL'
    UNION ALL SELECT 'UM',    'JPY', 'TWD', NULL,          'AUTO'
    UNION ALL SELECT 'VJP',   'USD', 'JPY', 148.50000000,  'MANUAL'
    UNION ALL SELECT 'VJP',   'EUR', 'JPY', NULL,          'AUTO'
    UNION ALL SELECT 'AU',    'USD', 'HKD', 7.85000000,    'MANUAL'
    UNION ALL SELECT 'MONETA','USD', 'SGD', NULL,          'AUTO'
) v
JOIN `brand` br ON br.code = v.brand_code
JOIN `currency` b ON b.code = v.base_code
JOIN `currency` q ON q.code = v.quote_code;
