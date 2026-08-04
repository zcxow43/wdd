DROP TABLE IF EXISTS currency;

CREATE TABLE currency (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    code           VARCHAR(3)    NOT NULL,
    name           VARCHAR(100)  NOT NULL,
    name_zh        VARCHAR(100)  NULL,
    symbol         VARCHAR(10)   NULL,
    decimal_places INT           NOT NULL DEFAULT 2,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (code)
);

DROP TABLE IF EXISTS brand;

CREATE TABLE brand (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)   NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    active      TINYINT(1)    NOT NULL DEFAULT 1,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (code)
);

DROP TABLE IF EXISTS currency_pair;

-- Intentionally without FK constraints to brand/currency: multiple MockMvc controller test
-- classes (CurrencyControllerTest, BrandControllerTest, CurrencyPairControllerTest) share one
-- H2 in-memory database for the lifetime of the test JVM, and each class's own @BeforeEach
-- independently wipes/reseeds its own required tables. Cross-table FK enforcement here would
-- create ordering dependencies between otherwise-independent test classes; each pair test
-- resets currency_pair, brand, and currency itself for full isolation instead.
CREATE TABLE currency_pair (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    brand_id           BIGINT        NOT NULL,
    base_currency_id   BIGINT        NOT NULL,
    quote_currency_id  BIGINT        NOT NULL,
    rate               DECIMAL(18,8) NULL,
    rate_type          VARCHAR(10)   NOT NULL DEFAULT 'MANUAL',
    active             TINYINT(1)    NOT NULL DEFAULT 1,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (brand_id, base_currency_id, quote_currency_id)
);

DROP TABLE IF EXISTS audit_request;

-- Snapshot columns use VARCHAR(4000) rather than H2's JSON type: MyBatis treats the
-- MySQL JSON column as an opaque string either way, and this avoids H2-JSON-literal
-- binding quirks; functionally equivalent to the MySQL JSON columns from
-- specs/dba/audit.md's V005__create_audit_request_table.sql.
CREATE TABLE audit_request (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    entity_type     VARCHAR(30)   NOT NULL,
    action_type     VARCHAR(10)   NOT NULL,
    entity_id       BIGINT        NULL,
    before_snapshot VARCHAR(4000) NULL,
    after_snapshot  VARCHAR(4000) NULL,
    summary         VARCHAR(255)  NULL,
    status          VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    requested_by    VARCHAR(100)  NULL,
    requested_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by     VARCHAR(100)  NULL,
    reviewed_at     DATETIME      NULL,
    reject_reason   VARCHAR(255)  NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS spread_group_member;

DROP TABLE IF EXISTS spread_group;

DROP TABLE IF EXISTS spread_default;

-- Intentionally without FK constraints, matching currency_pair's own convention above (see its
-- comment) — each spread test class independently wipes/reseeds brand/currency/currency_pair/
-- spread_* for full isolation instead of relying on cross-table FK enforcement here.
CREATE TABLE spread_default (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    brand_id        BIGINT        NOT NULL,
    deposit_spread  DECIMAL(18,8) NOT NULL DEFAULT 0,
    withdraw_spread DECIMAL(18,8) NOT NULL DEFAULT 0,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (brand_id)
);

CREATE TABLE spread_group (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    brand_id        BIGINT        NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    deposit_spread  DECIMAL(18,8) NOT NULL,
    withdraw_spread DECIMAL(18,8) NOT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (brand_id, name)
);

CREATE TABLE spread_group_member (
    id                BIGINT   NOT NULL AUTO_INCREMENT,
    spread_group_id   BIGINT   NOT NULL,
    currency_pair_id  BIGINT   NOT NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (currency_pair_id)
);

DROP TABLE IF EXISTS currency_pair_definition;

-- Intentionally without the production unique constraint on the generated (pair_key_low,
-- pair_key_high) columns (specs/dba/currency-pair-definition.md): H2's generated-column/unique-
-- index combination isn't replicated here, consistent with how currency_pair above already omits
-- FKs/DB-level guards that are production-only defense-in-depth. The service-layer
-- findByEitherDirection pre-check is what the tests exercise; the DB constraint remains the
-- backstop in production.
CREATE TABLE currency_pair_definition (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    base_currency_id   BIGINT        NOT NULL,
    quote_currency_id  BIGINT        NOT NULL,
    forward_precision  TINYINT       NOT NULL,
    reverse_precision  TINYINT       NOT NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
