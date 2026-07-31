-- V011__reset_currency_pair_data.sql
-- One-time data reset. currency_pair_definition is the parent; creating one
-- fans out currency_pair rows to every brand (specs/backend/
-- currency-pair-definition.md). Every currency_pair row in this database was
-- inserted by V003/V004, before that parent->child mechanism existed, so none
-- of it has a parent definition — all of it is orphaned. Wipe it, along with
-- the (in practice empty) currency_pair_definition table and the now-
-- meaningless CURRENCY_PAIR audit history, so every future currency_pair row
-- is created exclusively through the parent -> child fan-out going forward.
-- spread_group_member rows referencing a deleted currency_pair are removed
-- automatically by its existing ON DELETE CASCADE FK (specs/dba/spread.md).
-- User-authorized data reset; no schema change.
-- Rollback: not reversible — restore from a backup taken before this ran.

DELETE FROM `audit_request` WHERE `entity_type` = 'CURRENCY_PAIR';
DELETE FROM `currency_pair`;
DELETE FROM `currency_pair_definition`;
