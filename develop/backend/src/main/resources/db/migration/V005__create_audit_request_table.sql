-- V005__create_audit_request_table.sql
-- Creates audit_request: the standalone, entity-agnostic approval-workflow
-- table backing the audit module (specs/backend/audit.md). Any feature that
-- needs create/update/delete to go through review plugs into this same
-- table via its own entity_type value and snapshot shape — no schema change
-- to this table is ever required to add a new consumer.
-- Rollback: DROP TABLE IF EXISTS `audit_request`;

CREATE TABLE IF NOT EXISTS `audit_request` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT,
    `entity_type`     VARCHAR(30)    NOT NULL,
    `action_type`     VARCHAR(10)    NOT NULL,
    `entity_id`       BIGINT         NULL,
    `before_snapshot` JSON           NULL,
    `after_snapshot`  JSON           NULL,
    `summary`         VARCHAR(255)   NULL,
    `status`          VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    `requested_by`    VARCHAR(100)   NULL,
    `requested_at`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `reviewed_by`     VARCHAR(100)   NULL,
    `reviewed_at`     DATETIME       NULL,
    `reject_reason`   VARCHAR(255)   NULL,
    `created_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_audit_request_status` (`status`),
    KEY `idx_audit_request_entity` (`entity_type`, `entity_id`),
    CONSTRAINT `ck_audit_request_action_type` CHECK (`action_type` IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT `ck_audit_request_status` CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
