# backend

Version: 0.0.4

Spring Boot REST API for currency, brand and currency pair management (`pl.piomin.services`).

## Stack
- Java 17, Spring Boot 3.5.16, Maven
- MyBatis (mapper interface + XML) over MySQL 8
- Bean Validation (jakarta.validation)
- Tests: JUnit 5, Mockito, MockMvc against an in-memory H2 database

## Run

```
mvn -f develop/backend/pom.xml spring-boot:run
```

Requires MySQL reachable at `127.0.0.1:3306`, database `wdd`, user `app` (see `env.md`).
The `currency`, `brand` and `currency_pair` tables must already exist (see `specs/dba/currency.md`, `specs/dba/brand.md`, `specs/dba/currency-pair.md`).

## Build & Test

```
mvn -f develop/backend/pom.xml compile
mvn -f develop/backend/pom.xml test
```

## API

Base path: `/api/currencies`

| Method | Path                | Description                        |
|--------|---------------------|-------------------------------------|
| GET    | /api/currencies      | List currencies (optional `?active=`) |
| GET    | /api/currencies/{id} | Get one currency                    |
| POST   | /api/currencies      | Create a currency                   |
| PUT    | /api/currencies/{id} | Partially update a currency         |
| DELETE | /api/currencies/{id} | Delete a currency                   |

See `specs/backend/currency.md` for the full contract, validation rules, and error responses.

Base path: `/api/brands`

| Method | Path              | Description                              |
|--------|-------------------|--------------------------------------------|
| GET    | /api/brands        | List brands (optional `?active=`)          |
| GET    | /api/brands/{id}   | Get one brand                              |
| PUT    | /api/brands/{id}   | Toggle a brand's `active` flag              |

Brands are a fixed, seeded set (AU, MONETA, PUG, STAR, UM, VJP, VT) — there is no create or delete endpoint. See `specs/backend/brand.md` for the full contract, validation rules, and error responses.

Base path: `/api/currency-pairs`

| Method | Path                     | Description                                    |
|--------|--------------------------|--------------------------------------------------|
| GET    | /api/currency-pairs       | List currency pairs (optional `?brandId=`, `?active=`) |
| GET    | /api/currency-pairs/{id}  | Get one currency pair                             |
| POST   | /api/currency-pairs       | Create a currency pair                            |
| PUT    | /api/currency-pairs/{id}  | Partially update a currency pair                  |
| DELETE | /api/currency-pairs/{id}  | Delete a currency pair                            |

Each currency pair belongs to exactly one brand and has a `rate`/`rateType` (`MANUAL` or `AUTO`); (brand, base, quote) must be unique, and base/quote currencies must differ. See `specs/backend/currency-pair.md` for the full contract, validation rules, and error responses.

Two related changes ship alongside this feature:
- `PUT /api/currencies/{id}` no longer accepts/changes `code` — a currency's code is immutable once created.
- `DELETE /api/currencies/{id}` now returns `409` if the currency is still referenced (as base or quote) by any currency pair.

## Version History
- 0.0.1 — Initial Currency CRUD API (list/get/create/update/delete), validation, error handling, unit + integration tests.
- 0.0.2 — Added Brand API (list/get/toggle-active), reusing the `brand` table seeded by `specs/dba/brand.md`; unit + integration tests.
- 0.0.3 — Added Currency Pair CRUD API (`/api/currency-pairs`), scoped per brand with brand/currency joins for enriched responses; made currency `code` immutable on update; currency delete now blocked (`409`) while referenced by a currency pair; unit + integration tests.
- 0.0.4 — Currency Pair rate/rateType rule: `MANUAL` requires rate (non-null, >0); `AUTO` forces rate to null (ignores supplied values). Enforced at service layer on create/update; comprehensive unit + integration tests covering all branches.
