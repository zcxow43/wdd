-- V010__drop_currency_active_column.sql
-- Drops currency.active: currency has no enable/disable concept — every row
-- is always usable once created (delete via the existing in-use guard
-- instead, specs/backend/currency.md). Purely additive elsewhere — no
-- other table is touched.
-- Rollback: ALTER TABLE `currency` ADD COLUMN `active` TINYINT(1) NOT NULL DEFAULT 1;
--           (rolled-back rows would all read as 1/active, indistinguishable from before)

ALTER TABLE `currency` DROP COLUMN `active`;
