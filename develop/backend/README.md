# backend

Version: 0.0.2

Spring Boot REST API for currency and brand management (`pl.piomin.services`).

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
The `currency` and `brand` tables must already exist (see `specs/dba/currency.md`, `specs/dba/brand.md`).

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

## Version History
- 0.0.1 — Initial Currency CRUD API (list/get/create/update/delete), validation, error handling, unit + integration tests.
- 0.0.2 — Added Brand API (list/get/toggle-active), reusing the `brand` table seeded by `specs/dba/brand.md`; unit + integration tests.
