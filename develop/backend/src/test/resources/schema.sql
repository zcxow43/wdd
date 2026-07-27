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
    rate               DECIMAL(18,8)  NOT NULL,
    rate_type          VARCHAR(10)    NOT NULL DEFAULT 'MANUAL',
    active             TINYINT(1)     NOT NULL DEFAULT 1,
    created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_currency_pair_brand_base_quote UNIQUE (brand_id, base_currency_id, quote_currency_id)
);
