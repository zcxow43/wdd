---
status: done
title: "Currency API"
requirement: "新增幣種功能，要可以 CRUD（新增/查詢/修改/刪除）"
depends_on: []
---

# Currency — Backend Spec

## Overview
Full CRUD REST API for the currency master list (see [currency.md](../dba/currency.md)). Unlike Brand, currencies are created and deleted through the API, not seeded — `code` is immutable once set, but every other field can be updated.

## Requirements

### Entity: Currency
| Field | Type | Rule |
|---|---|---|
| id | Long | PK |
| code | String | Required on create; 3 uppercase letters (`^[A-Z]{3}$`); unique; immutable after creation |
| name | String | Required; updatable |
| symbol | String | Required; updatable |
| decimalPlaces | Integer | Required; range 0–8; updatable |
| createdAt / updatedAt | Timestamp | System maintained |

### API Contract

**GET /api/currencies**
- Response `200`: `[ { "id": 1, "code": "USD", "name": "US Dollar", "symbol": "$", "decimalPlaces": 2, "createdAt": "...", "updatedAt": "..." }, ... ]`

**GET /api/currencies/{id}**
- Response `200`: single Currency object (same shape as above).
- Not found → `404`.

**POST /api/currencies**
- Request body: `{ "code": "USD", "name": "US Dollar", "symbol": "$", "decimalPlaces": 2 }`
- Validation: `code` required, matches `^[A-Z]{3}$`, not already in use; `name`/`symbol` required non-blank; `decimalPlaces` required, integer 0–8. Any violation → `400`.
- `code` already exists → `409`.
- Response `201`: the created Currency object.

**PUT /api/currencies/{id}**
- Request body: `{ "name": "US Dollar", "symbol": "$", "decimalPlaces": 2 }` — `code` is not accepted; if present in the request body it is ignored (the stored `code` never changes).
- Validation: same as create for `name`/`symbol`/`decimalPlaces`. Violation → `400`.
- `id` not found → `404`.
- Response `200`: the updated Currency object.

**DELETE /api/currencies/{id}**
- `id` not found → `404`.
- Response `204` on success, row removed.

## Implementation Details
1. `GET /api/currencies` / `GET /api/currencies/{id}` read the live table directly; `404` via a not-found exception when no row matches.
2. `POST /api/currencies`: validate request body → check `code` uniqueness (`409` if taken) → insert → return `201` with the created row.
3. `PUT /api/currencies/{id}`: load existing row (`404` if missing) → validate `name`/`symbol`/`decimalPlaces` (`400` if invalid) → update those three columns only, `code` untouched → return the refreshed row.
4. `DELETE /api/currencies/{id}`: load existing row (`404` if missing) → delete → `204`.
5. All mutations apply directly; none of this goes through an audit/approval flow.

## Acceptance Criteria
- [x] `GET /api/currencies` returns all currencies.
- [x] `GET /api/currencies/{id}` returns `404` for a non-existent id.
- [x] `POST /api/currencies` with a valid body creates a currency and returns `201` with the created object.
- [x] `POST /api/currencies` with a duplicate `code` returns `409`.
- [x] `POST /api/currencies` with an invalid `code` format (not 3 uppercase letters), missing `name`/`symbol`, or `decimalPlaces` outside 0–8 returns `400`.
- [x] `PUT /api/currencies/{id}` updates `name`/`symbol`/`decimalPlaces` and leaves `code` unchanged even if a different `code` is sent in the body.
- [x] `PUT /api/currencies/{id}` for a non-existent id returns `404`.
- [x] `DELETE /api/currencies/{id}` removes the row and returns `204`.
- [x] `DELETE /api/currencies/{id}` for a non-existent id returns `404`.

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/backend/src/main/java/com/wdd/backend/dto/Currency.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyResponse.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyCreateRequest.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyUpdateRequest.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/exception/CurrencyNotFoundException.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/exception/CurrencyCodeConflictException.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/exception/GlobalExceptionHandler.java` (added handlers for the two new exceptions, mapping to 404/409)
  - `develop/backend/src/main/java/com/wdd/backend/mapper/CurrencyMapper.java` (new)
  - `develop/backend/src/main/resources/mapper/CurrencyMapper.xml` (new)
  - `develop/backend/src/main/java/com/wdd/backend/service/CurrencyService.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/controller/CurrencyController.java` (new)
  - `develop/backend/src/test/java/com/wdd/backend/service/CurrencyServiceTest.java` (new, 13 unit tests against a mocked mapper)
  - `develop/backend/src/test/java/com/wdd/backend/controller/CurrencyControllerTest.java` (new, 13 integration tests via `TestRestTemplate` against the live MySQL `wdd` database, RANDOM_PORT)
- Notes: Followed the existing Brand Controller → Service → MyBatis Mapper (interface + XML) → DTO layering, no Lombok. `code` immutability is enforced by `CurrencyUpdateRequest` simply having no `code` field (Spring's default Jackson config ignores unknown JSON properties, satisfying "if present in the request body it is ignored"). Validation (`code` regex `^[A-Z]{3}$`, non-blank `name`/`symbol`, `decimalPlaces` 0–8) and `code` uniqueness checks live in `CurrencyService`, throwing `InvalidRequestException` (400), `CurrencyCodeConflictException` (409, new `@ExceptionHandler`), or `CurrencyNotFoundException` (404) as appropriate — mirroring the `InvalidRequestException`/`BrandNotFoundException` pattern already registered in `GlobalExceptionHandler`. Controller tests create/clean up their own rows (unique 3-letter codes prefixed `QA*`) since the `currency` table is otherwise empty and shared across the whole test run; verified via direct MySQL query that the table is empty both before and after `mvn test`. Ran `mvn -f develop/backend/pom.xml compile` (clean) and `mvn -f develop/backend/pom.xml test` (40 tests total across the module, 0 failures/errors, including the 26 new Currency tests) against the live `wdd-mysql` Docker container.
