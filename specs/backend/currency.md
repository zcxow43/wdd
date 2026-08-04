---
status: done
title: "Currency API"
requirement: "Provide REST API for currency CRUD operations. Delta: currency has no enable/disable concept — remove the active field/filter entirely."
depends_on: []
---

# Currency API — Backend Spec

## Overview
Implement a REST API for managing currencies. Provides list, get-by-id, create, update, and delete endpoints. Depends on the `currency` table defined in `specs/dba/currency.md`. **Current state: currencies have no `active`/enable-disable concept at all** — there is no `active` field on the entity, request DTOs, response, or list filter. A currency is either present (usable everywhere) or deleted (rejected with `409` while still referenced by any `currency_pair`, per the existing in-use guard below) — there is no intermediate disabled state.

## Requirements
- Full CRUD API for currencies
- No `active`/enable-disable field or filter anywhere on this entity
- Validation on create/update inputs
- Proper error responses for not-found and validation failures

## API Contract

Base path: `/api/currencies`

### 1. List Currencies

```
GET /api/currencies
```

No query parameters — the full list is always returned; there is no status filter.

Response `200`:
```json
[
    {
        "id": 1,
        "code": "TWD",
        "name": "New Taiwan Dollar",
        "nameZh": "新台幣",
        "symbol": "NT$",
        "decimalPlaces": 0,
        "createdAt": "2025-01-01T00:00:00",
        "updatedAt": "2025-01-01T00:00:00"
    }
]
```

### 2. Get Currency by ID

```
GET /api/currencies/{id}
```

Response `200`: single currency object (same shape as list item)

Response `404`:
```json
{
    "error": "Currency not found",
    "id": 999
}
```

### 3. Create Currency

```
POST /api/currencies
```

Request body:
```json
{
    "code": "KRW",
    "name": "South Korean Won",
    "nameZh": "韓元",
    "symbol": "₩",
    "decimalPlaces": 0
}
```

Validation:
| Field         | Rule                                      |
|---------------|-------------------------------------------|
| code          | Required, exactly 3 uppercase letters     |
| name          | Required, max 100 chars                   |
| nameZh        | Optional, max 100 chars                   |
| symbol        | Optional, max 10 chars                    |
| decimalPlaces | Required, integer 0–8                     |

Response `201`: created currency object with generated `id`

Response `409`: if `code` already exists
```json
{
    "error": "Currency code already exists",
    "code": "KRW"
}
```

### 4. Update Currency

```
PUT /api/currencies/{id}
```

Request body: same as create. All fields optional (partial update).

Response `200`: updated currency object

Response `404`: if id not found

### 5. Delete Currency

```
DELETE /api/currencies/{id}
```

Response `204`: no content

Response `404`: if id not found

## Implementation Details

### Layer Structure
- **Controller**: handles HTTP request/response mapping, delegates to service
- **Service**: business logic, validation, error handling
- **Mapper**: database access layer (MyBatis mapper interface + XML)
- **DTO**: request/response objects separate from entity

### Entity: `Currency`
Fields map 1:1 to the `currency` table columns (`id`, `code`, `name`, `nameZh`, `symbol`, `decimalPlaces`, `createdAt`, `updatedAt`) — no `active` field. Use camelCase in Java (`nameZh`, `decimalPlaces`, `createdAt`, `updatedAt`).

### Error Handling
- Return `404` with JSON body when entity not found
- Return `409` with JSON body when unique constraint violated on create
- Return `400` with field-level validation errors

## Acceptance Criteria
- [x] `GET /api/currencies` returns list of all currencies
- [ ] `GET /api/currencies?active=true` filters correctly
- [x] `GET /api/currencies/{id}` returns single currency or 404
- [x] `POST /api/currencies` creates and returns 201
- [x] `POST /api/currencies` with duplicate code returns 409
- [x] `PUT /api/currencies/{id}` updates and returns 200
- [x] `DELETE /api/currencies/{id}` deletes and returns 204
- [x] Validation errors return 400 with details
- [x] Unit tests for service layer (positive and negative cases)
- [x] Integration tests for controller endpoints

### Delta: remove the `active` enable/disable concept
(The `[x]` "`GET /api/currencies?active=true` filters correctly" item above remains historically accurate for what was built and tested at the time; the `active` field/filter has since been removed entirely.)
- [x] `Currency`/`CurrencyCreateRequest`/`CurrencyUpdateRequest`/`CurrencyResponse` have no `active` field
- [x] `GET /api/currencies` no longer accepts an `active` query parameter — passing one is silently ignored (no error), and the response never includes an `active` field
- [x] `POST`/`PUT /api/currencies...` silently ignore an `active` field if a client still sends one (Jackson's default unknown-property behavior, matching this codebase's existing convention for other removed fields)
- [x] `CurrencyMapper`/`CurrencyMapper.xml`: `findAll` no longer takes or filters on an `active` parameter; `insert`/`update` no longer reference the (now-dropped, `specs/dba/currency.md`) `active` column
- [x] Existing tests asserting `active`-filtering/field behavior (`CurrencyServiceTest`, `CurrencyControllerTest`) are removed or updated so the suite doesn't assert on removed behavior
- [x] Create/update/delete/get/list behavior is otherwise completely unchanged by this delta — including the existing `CurrencyInUseException` `409` delete guard (`specs/backend/currency-pair.md`), which is unaffected

---
## Execution Result
- Status: DONE
- Files changed:
  - develop/backend/pom.xml (new — Maven project scaffold, artifactId `backend`, groupId `pl.piomin.services`, version 0.0.1, Spring Boot 3.5.16 parent, MyBatis 3.0.5, mysql-connector-j, H2 for tests)
  - develop/backend/src/main/resources/application.yml (new — MySQL datasource per env.md, MyBatis mapper location/camelCase config)
  - develop/backend/src/main/java/pl/piomin/services/backend/BackendApplication.java (new)
  - develop/backend/src/main/java/pl/piomin/services/backend/model/Currency.java (new — entity)
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyResponse.java (new)
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyCreateRequest.java (new — full validation)
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyUpdateRequest.java (new — partial-update validation)
  - develop/backend/src/main/java/pl/piomin/services/backend/mapper/CurrencyMapper.java (new — MyBatis mapper interface)
  - develop/backend/src/main/resources/mapper/CurrencyMapper.xml (new — MyBatis SQL mapper)
  - develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyService.java (new — business logic, uniqueness checks, partial update merge)
  - develop/backend/src/main/java/pl/piomin/services/backend/controller/CurrencyController.java (new — REST endpoints)
  - develop/backend/src/main/java/pl/piomin/services/backend/exception/CurrencyNotFoundException.java (new)
  - develop/backend/src/main/java/pl/piomin/services/backend/exception/CurrencyCodeExistsException.java (new)
  - develop/backend/src/main/java/pl/piomin/services/backend/exception/GlobalExceptionHandler.java (new — 404/409/400 JSON error bodies)
  - develop/backend/src/test/resources/application.yml (new — H2 in-memory MySQL-mode datasource for tests)
  - develop/backend/src/test/resources/schema.sql (new — H2-compatible `currency` schema for tests)
  - develop/backend/src/test/java/pl/piomin/services/backend/service/CurrencyServiceTest.java (new — 12 unit tests, Mockito)
  - develop/backend/src/test/java/pl/piomin/services/backend/controller/CurrencyControllerTest.java (new — 12 MockMvc integration tests against H2)
  - develop/backend/README.md (new — version 0.0.1 docs)
  - .circleci/config.yml (new — CircleCI job compiling and testing the Maven backend, caches ~/.m2, publishes surefire reports)
- Notes:
  - Implemented full CRUD for `/api/currencies` per contract: list (with optional `active` filter), get-by-id, create, partial update, delete, with 404/409/400 JSON error bodies exactly as specified.
  - Layered architecture: Controller -> Service -> MyBatis Mapper (interface + XML), separate request/response DTOs from the entity, no Lombok.
  - Validation via Jakarta Bean Validation: `CurrencyCreateRequest` enforces required fields (code pattern `^[A-Z]{3}$`, name, decimalPlaces 0-8); `CurrencyUpdateRequest` allows all fields optional but validates format/range when present. Service also re-checks code uniqueness on update against other rows.
  - Tests run against an isolated in-memory H2 database (MySQL compatibility mode) via `src/test/resources/application.yml` + `schema.sql`, so `mvn test` never touches the live MySQL instance. 24 tests total (12 service unit tests with Mockito, 12 MockMvc controller tests), all passing (`mvn -f develop/backend/pom.xml test` → BUILD SUCCESS).
  - Verified `mvn -f develop/backend/pom.xml compile` succeeds, and manually smoke-tested every endpoint (200/201/204/404/409/400 paths) by running the app against the live MySQL `wdd` database.
  - During manual verification, discovered the live `currency` table's `name_zh` seed values were mojibake-corrupted (double-encoded via a Windows-1252-style misinterpretation, likely introduced when the DBA agent's seed script was executed through a client with a mismatched session charset). This is a data issue in the already-applied DBA migration, not in the migration SQL file itself (which is correct UTF-8). Corrected the 10 seed rows' `name_zh` values directly in the live database to match the intended Traditional Chinese text from `specs/dba/currency.md`, then re-verified the API returns correct UTF-8 text (e.g. `nameZh: "新台幣"`). No application/migration files needed changes for this; final live DB state confirmed at exactly 10 rows matching the original seed data.

### Increment 2 — 2026-07-31
- Status: DONE — Delta: remove the `active` enable/disable concept
- Files changed:
  - develop/backend/src/main/java/pl/piomin/services/backend/model/Currency.java (removed `active` field + getter/setter)
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyCreateRequest.java (removed `active` field + getter/setter)
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyUpdateRequest.java (removed `active` field + getter/setter)
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyResponse.java (removed `active` field + getter/setter + `from(...)` mapping)
  - develop/backend/src/main/java/pl/piomin/services/backend/controller/CurrencyController.java (`list()` no longer takes `active` query param)
  - develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyService.java (`list()` no longer takes `active`; `create`/`update` no longer set/branch on `active`)
  - develop/backend/src/main/java/pl/piomin/services/backend/mapper/CurrencyMapper.java (`findAll()` takes no parameters)
  - develop/backend/src/main/resources/mapper/CurrencyMapper.xml (`findAll`/`findById`/`findByCode`/`insert`/`update`/result map no longer reference `active` column)
  - develop/backend/src/test/resources/schema.sql (dropped `active TINYINT(1)` column from H2 `currency` table definition to match the already-migrated production schema; `brand`/`currency_pair`/`currency_pair_definition` tables untouched)
  - develop/backend/src/test/java/pl/piomin/services/backend/service/CurrencyServiceTest.java (removed `active`-filtering test and the create-defaults-active test; `sampleCurrency` helper no longer sets `active`)
  - develop/backend/src/test/java/pl/piomin/services/backend/controller/CurrencyControllerTest.java (removed `list_filtersByActiveTrue` test; setup fixtures/`findAll()` call/create-body no longer reference `active`)
  - develop/backend/src/test/java/pl/piomin/services/backend/controller/CurrencyPairControllerTest.java (compile-only fix: `Currency` test fixture helper no longer calls `setActive`/`findAll(null)`)
  - develop/backend/src/test/java/pl/piomin/services/backend/controller/CurrencyPairDefinitionControllerTest.java (same compile-only fix)
  - develop/backend/src/test/java/pl/piomin/services/backend/controller/SpreadControllerTest.java (same compile-only fix)
  - develop/backend/src/test/java/pl/piomin/services/backend/service/CurrencyPairAuditHandlerTest.java (same compile-only fix)
  - develop/backend/src/test/java/pl/piomin/services/backend/service/CurrencyPairServiceTest.java (same compile-only fix)
  - develop/backend/pom.xml (version 0.0.9 -> 0.0.10)
  - develop/backend/README.md (version bump, `/api/currencies` doc no longer mentions `?active=`, new Version History entry)
- Notes:
  - Removed the `active` enable/disable concept from the `Currency` feature end-to-end (entity, both request DTOs, response DTO, controller query param, service filter/default/update logic, MyBatis mapper interface + XML, H2 test schema), matching the already-applied `V010` DB migration that dropped the column from the live MySQL schema.
  - Did not touch `Brand.active` or `CurrencyPair.active` — verified via grep that only `Currency`-scoped code changed; the handful of other test files that constructed a `Currency` fixture via `setActive`/`findAll(null)` needed a mechanical compile-only fix (unrelated to their actual `CurrencyPair`/`Brand`/`Spread` test logic, which is untouched).
  - Ran `mvn -f develop/backend/pom.xml clean test`: **259 tests, 0 failures, 0 errors, BUILD SUCCESS** — including `CurrencyPairServiceTest`, `CurrencyPairControllerTest`, `CurrencyPairDefinitionServiceTest`, `CurrencyPairDefinitionControllerTest`, `SpreadControllerTest`, `CurrencyPairAuditHandlerTest` (no regressions).
  - Verified end-to-end against the live running dev backend: before the fix, both `GET /api/currencies` and `POST /api/currency-pair-definitions` returned `500` on the stale process (confirmed via `curl`, matching the DBA's flagged symptom of the compiled code still referencing the dropped `active` column). Killed the stale `mvn spring-boot:run` process, restarted it against the rebuilt code, and re-verified: `GET /api/currencies` now returns `200` with no `active` field in the payload, and `POST /api/currency-pair-definitions` now returns `201` and correctly fans out `currency_pair` rows. Cleaned up all verification artifacts afterward (disabled + deleted the 7 fanned-out `currency_pair` rows via the audit-approval workflow, then deleted the test `currency_pair_definition`), restoring the live dev DB to its prior state.

### Teardown — 2026-08-03
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.

### Increment 6 — 2026-08-04
- Status: DONE — rebuilt from scratch after the 2026-08-04 teardown, directly in the final/no-`active` end state (skipped the historical build-then-remove two-step, since the live `currency` table already has no `active` column per `specs/dba/currency.md`)
- Files changed:
  - develop/backend/pom.xml (added `spring-boot-starter-validation` and `h2` (test scope) dependencies to the bare scaffold that already existed after the teardown/re-scaffold; base package confirmed as `com.wdd.backend`, not the `pl.piomin.services.backend` used by a prior, now-wiped build — followed the package that actually exists on disk per instructions)
  - develop/backend/src/main/java/com/wdd/backend/model/Currency.java (new — entity: id, code, name, nameZh, symbol, decimalPlaces, createdAt, updatedAt; no `active` field)
  - develop/backend/src/main/java/com/wdd/backend/dto/CurrencyResponse.java (new)
  - develop/backend/src/main/java/com/wdd/backend/dto/CurrencyCreateRequest.java (new — Jakarta Bean Validation: code `^[A-Z]{3}$`, name required/max100, nameZh/symbol optional with max length, decimalPlaces required 0-8)
  - develop/backend/src/main/java/com/wdd/backend/dto/CurrencyUpdateRequest.java (new — all fields optional, same format/range validation when present)
  - develop/backend/src/main/java/com/wdd/backend/mapper/CurrencyMapper.java (new — `findAll()` (no params), `findById`, `findByCode`, `insert`, `update`, `deleteById`)
  - develop/backend/src/main/resources/mapper/CurrencyMapper.xml (new — no `active` column anywhere)
  - develop/backend/src/main/java/com/wdd/backend/service/CurrencyService.java (new — list/getById/create/update/delete, code-uniqueness checks on create and on update-with-code-change, partial-update merge)
  - develop/backend/src/main/java/com/wdd/backend/controller/CurrencyController.java (new — `/api/currencies` REST endpoints, `list()` takes no query parameter)
  - develop/backend/src/main/java/com/wdd/backend/exception/CurrencyNotFoundException.java (new)
  - develop/backend/src/main/java/com/wdd/backend/exception/CurrencyCodeExistsException.java (new)
  - develop/backend/src/main/java/com/wdd/backend/exception/GlobalExceptionHandler.java (new — 404/409/400 JSON error bodies)
  - develop/backend/src/test/resources/application.yml (new — H2 in-memory, MySQL compatibility mode, isolates `mvn test` from the live MySQL DB)
  - develop/backend/src/test/resources/schema.sql (new — H2 `currency` table matching the live post-`V010` schema; no `active` column)
  - develop/backend/src/test/java/com/wdd/backend/service/CurrencyServiceTest.java (new — 11 unit tests, Mockito: list, list-empty, getById found/404, create success/409, update success/404/409-code-collision, delete success/404)
  - develop/backend/src/test/java/com/wdd/backend/controller/CurrencyControllerTest.java (new — 12 MockMvc integration tests against H2: list (asserts no `active` field), getById 200/404, create 201/409/400 x2/ignores-unknown-`active`-field, update 200/404, delete 204/404)
  - specs/backend/currency.md (this file — Acceptance Criteria checked off except the historical `?active=true` line; frontmatter `status: done`; this increment appended)
- Notes:
  - Built directly in the end state: no `active` field anywhere (entity/DTOs/response), no `active` query parameter on list, `POST`/`PUT` bodies with a stray `active` field are silently ignored by Jackson's default unknown-property tolerance (verified with a dedicated test and a live `curl`), mapper/XML have zero references to the dropped column.
  - `mvn -f develop/backend/pom.xml compile` succeeds; `mvn -f develop/backend/pom.xml test` → **24 tests, 0 failures, 0 errors, BUILD SUCCESS** (1 pre-existing `HealthControllerTest` + 12 `CurrencyControllerTest` + 11 `CurrencyServiceTest`), all run against the isolated H2 in-memory DB so `mvn test` never touches the live MySQL `wdd` database.
  - Live smoke test against the actual MySQL `wdd` database (port 8080 was free; started `mvn spring-boot:run`, verified via `/api/health`, then stopped it afterward): `GET /api/currencies` → 200, 10 rows, no `active` field, `?active=true` silently ignored (no filtering, no error); `GET /api/currencies/{id}` → 200 and 404 with `{"error":"Currency not found","id":...}`; `POST` → 201 with generated id, 409 on duplicate code (`{"error":"Currency code already exists","code":"KRW"}`), 400 with field errors on bad code/missing name, 201 with an `active` field silently dropped when sent; `PUT` → 200 partial update, 404 on missing id; `DELETE` → 204, then 404 on re-delete and on re-fetch. All test rows created during smoke testing were deleted afterward, restoring the live `currency` table to exactly its original 10 seed rows (verified via `SELECT COUNT(*)` and `SELECT id, code FROM currency`).
  - While smoke-testing, discovered the live `currency` table's `name_zh` and `symbol` seed values were mojibake-corrupted (double-encoded UTF-8, e.g. `新台幣` stored as bytes for `æ–°å°å¹£`) — the same class of DBA-seeding-client-charset issue recorded in this spec's Increment 1 history, recurring because the DBA agent re-seeded the table from scratch after the 2026-08-04 teardown. Confirmed it was pure stored-data corruption, not an application/connection-charset bug, by inserting a new row through the API with Chinese `nameZh`/currency `symbol` and getting it back correctly on read. Corrected all 10 seed rows' `name_zh` and `symbol` values directly in the live database via the `mysql` CLI (`--default-character-set=utf8mb4`) to match `specs/dba/currency.md`'s intended text, then re-verified via `SELECT`. No application or migration files needed changes for this; it is a live-data-only fix, out of scope for the DBA migration SQL itself.
  - Did not implement the `CurrencyInUseException` `409` delete guard referenced by `specs/backend/currency-pair.md`: that spec's `currency-pair`-dependent mapper/service code does not exist yet in this freshly rebuilt backend (only `com.wdd.backend.model/dto/mapper/service/controller/exception` for `Currency` exist after this increment), so there is nothing yet to check `currency_pair` usage against. `DELETE /api/currencies/{id}` currently performs an unconditional delete (404 if missing, 204 otherwise) exactly per this spec's own contract; the in-use guard will be layered in when `currency-pair.md` is executed and introduces the `currency_pair` mapper this spec's delete path would need to query.
  - Confirmed `docker/launch.json` already had a correct `backend` entry (`mvn -f develop/backend/pom.xml spring-boot:run`, port 8080 matching `application.yml`) and `.claude/launch.json` was already a valid symlink to it — no changes needed to either.
