DROP TABLE IF EXISTS currency;

CREATE TABLE currency (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(3)    NOT NULL,
    name           VARCHAR(100)  NOT NULL,
    name_zh        VARCHAR(100)  NULL,
    symbol         VARCHAR(10)   NULL,
    decimal_places INT           NOT NULL DEFAULT 2,
    active         TINYINT(1)    NOT NULL DEFAULT 1,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_currency_code UNIQUE (code)
);

DROP TABLE IF EXISTS brand;

CREATE TABLE brand (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(20)   NOT NULL,
    name       VARCHAR(100)  NOT NULL,
    active     TINYINT(1)    NOT NULL DEFAULT 1,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_brand_code UNIQUE (code)
);

DROP TABLE IF EXISTS currency_pair;

CREATE TABLE currency_pair (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id           BIGINT         NOT NULL,
    base_currency_id   BIGINT         NOT NULL,
    quote_currency_id  BIGINT         NOT NULL,
    rate               DECIMAL(18,8)  NULL,
    rate_type          VARCHAR(10)    NOT NULL DEFAULT 'MANUAL',
    active             TINYINT(1)     NOT NULL DEFAULT 1,
    created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_currency_pair_brand_base_quote UNIQUE (brand_id, base_currency_id, quote_currency_id)
);

DROP TABLE IF EXISTS audit_request;

CREATE TABLE audit_request (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type     VARCHAR(30)    NOT NULL,
    action_type     VARCHAR(10)    NOT NULL,
    entity_id       BIGINT         NULL,
    before_snapshot VARCHAR(4000)  NULL,
    after_snapshot  VARCHAR(4000)  NULL,
    summary         VARCHAR(255)   NULL,
    status          VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    requested_by    VARCHAR(100)   NULL,
    requested_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by     VARCHAR(100)   NULL,
    reviewed_at     DATETIME       NULL,
    reject_reason   VARCHAR(255)   NULL,
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS spread_group_member;
DROP TABLE IF EXISTS spread_group;
DROP TABLE IF EXISTS spread_default;

CREATE TABLE spread_default (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id        BIGINT         NOT NULL,
    deposit_spread  DECIMAL(18,8)  NOT NULL DEFAULT 0,
    withdraw_spread DECIMAL(18,8)  NOT NULL DEFAULT 0,
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_spread_default_brand UNIQUE (brand_id)
);

CREATE TABLE spread_group (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id        BIGINT         NOT NULL,
    name            VARCHAR(100)   NOT NULL,
    deposit_spread  DECIMAL(18,8)  NOT NULL,
    withdraw_spread DECIMAL(18,8)  NOT NULL,
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_spread_group_brand_name UNIQUE (brand_id, name)
);

CREATE TABLE spread_group_member (
    id                BIGINT   AUTO_INCREMENT PRIMARY KEY,
    spread_group_id   BIGINT   NOT NULL,
    currency_pair_id  BIGINT   NOT NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_spread_group_member_currency_pair UNIQUE (currency_pair_id)
);

DROP TABLE IF EXISTS currency_pair_definition;

CREATE TABLE currency_pair_definition (
    id                 BIGINT   AUTO_INCREMENT PRIMARY KEY,
    base_currency_id   BIGINT   NOT NULL,
    quote_currency_id  BIGINT   NOT NULL,
    forward_precision  TINYINT  NOT NULL,
    reverse_precision  TINYINT  NOT NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
