---
status: done
title: "Currency Pair Definition API"
requirement: "新增幣種對功能：CRUD、設定精度、無開關；新增時自動為所有品牌建立品牌幣種對；刪除前需所有品牌幣種對皆已關閉"
depends_on: [currency, brand]
---

# Currency Pair Definition — Backend Spec

## Overview
Global CRUD for currency pair definitions (see [currency-pair-definition.md](../dba/currency-pair-definition.md)). Creating a definition automatically fans out one [currency-pair.md](currency-pair.md) row per existing brand (all disabled, auto-rate). There is no active/inactive state on the definition itself; deleting one is blocked unless every brand's currency pair under it is already disabled.

## Requirements

### Entity: CurrencyPairDefinition
| Field | Type | Rule |
|---|---|---|
| id | Long | PK |
| baseCurrencyId | Long | Required on create; must reference an existing currency; immutable after creation |
| baseCurrencyCode | String | Read-only enrichment (joined from `currency.code`) |
| quoteCurrencyId | Long | Required on create; must reference an existing currency, different from `baseCurrencyId`; immutable after creation |
| quoteCurrencyCode | String | Read-only enrichment |
| precision | Integer | Optional on create (default 4); range 0–8; updatable |
| createdAt / updatedAt | Timestamp | System maintained |

### API Contract

**GET /api/currency-pair-definitions**
- Response `200`: `[ { "id": 1, "baseCurrencyId": 1, "baseCurrencyCode": "USD", "quoteCurrencyId": 2, "quoteCurrencyCode": "JPY", "precision": 4, "createdAt": "...", "updatedAt": "..." }, ... ]`

**GET /api/currency-pair-definitions/{id}**
- Response `200`: single object (same shape as above). Not found → `404`.

**POST /api/currency-pair-definitions**
- Request body: `{ "baseCurrencyId": 1, "quoteCurrencyId": 2, "precision": 4 }` (`precision` optional, defaults to 4).
- Validation: `baseCurrencyId`/`quoteCurrencyId` required and must reference existing currencies, and must differ from each other; `precision` if present must be an integer 0–8. Any violation → `400`.
- `(baseCurrencyId, quoteCurrencyId)` combination already exists → `409`.
- On success: insert the definition, then create one `currency_pair` row per existing brand (`rateType: "AUTO"`, `rate: null`, `active: false`) in the same transaction.
- Response `201`: the created definition plus the brand pairs it fanned out to: `{ ...definition fields..., "currencyPairs": [ { "id": 10, "brandId": 1, "brandCode": "au", "rateType": "AUTO", "rate": null, "active": false, ... }, ... ] }` (one entry per brand — 7 today).

**PUT /api/currency-pair-definitions/{id}**
- Request body: `{ "precision": 6 }` — `precision` is the only accepted field; `baseCurrencyId`/`quoteCurrencyId` are immutable and ignored if sent.
- Validation: `precision` integer 0–8 → `400` if invalid.
- Not found → `404`.
- Response `200`: the updated definition (same shape as GET).

**DELETE /api/currency-pair-definitions/{id}**
- Not found → `404`.
- If any `currency_pair` row under this definition has `active: true` → `409` with the list of brand codes still active: `{ "error": "Active brand currency pairs exist", "activeBrandCodes": ["au", "vt"] }`. Nothing is deleted.
- Otherwise: delete the definition — the database's `ON DELETE CASCADE` removes all of its (already-inactive) `currency_pair` rows too.
- Response `204` on success.

## Implementation Details
1. `GET` endpoints read the live tables directly, joining `currency` for the code enrichment fields.
2. `POST`: validate body (400s) → check currency existence (400 if either id doesn't exist) → check uniqueness of the pair (409) → in one transaction: insert the definition, read all brands, insert one `currency_pair` row per brand (`AUTO`/`null`/`false`) → return `201` with the definition and its fanned-out pairs.
3. `PUT`: load existing row (404 if missing) → validate `precision` (400 if invalid) → update only `precision` → return the refreshed row.
4. `DELETE`: load existing row (404 if missing) → query its `currency_pair` rows for any `active = true` (409 with `activeBrandCodes` if found) → otherwise delete the definition (cascade handles its `currency_pair` rows) → `204`.
5. All mutations apply directly; none of this goes through an audit/approval flow.

## Acceptance Criteria
- [x] `GET /api/currency-pair-definitions` returns all definitions with base/quote currency codes joined in.
- [x] `GET /api/currency-pair-definitions/{id}` returns `404` for a non-existent id.
- [x] `POST` with a valid body creates the definition and exactly one `currency_pair` row per existing brand (7 today), all `rateType: "AUTO"`, `rate: null`, `active: false`, returned in the response's `currencyPairs` array.
- [x] `POST` with a duplicate `(baseCurrencyId, quoteCurrencyId)` returns `409` and creates nothing.
- [x] `POST` with `baseCurrencyId == quoteCurrencyId`, a non-existent currency id, or an out-of-range `precision` returns `400`.
- [x] `PUT` updates `precision` only; a `baseCurrencyId`/`quoteCurrencyId` sent in the body is ignored.
- [x] `PUT`/`DELETE` for a non-existent id return `404`.
- [x] `DELETE` when any of its brand currency pairs is `active` returns `409` with `activeBrandCodes` and deletes nothing.
- [x] `DELETE` when all of its brand currency pairs are inactive succeeds and removes both the definition and its `currency_pair` rows.

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/backend/src/main/java/com/wdd/backend/controller/CurrencyPairDefinitionController.java`
  - `develop/backend/src/main/java/com/wdd/backend/service/CurrencyPairDefinitionService.java`
  - `develop/backend/src/main/java/com/wdd/backend/mapper/CurrencyPairDefinitionMapper.java`
  - `develop/backend/src/main/java/com/wdd/backend/mapper/CurrencyPairMapper.java`
  - `develop/backend/src/main/resources/mapper/CurrencyPairDefinitionMapper.xml`
  - `develop/backend/src/main/resources/mapper/CurrencyPairMapper.xml`
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairDefinition.java`
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairDefinitionResponse.java`
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairDefinitionCreateRequest.java`
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairDefinitionUpdateRequest.java`
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairDefinitionCreateResponse.java`
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPair.java`
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairResponse.java`
  - `develop/backend/src/main/java/com/wdd/backend/exception/CurrencyPairDefinitionNotFoundException.java`
  - `develop/backend/src/main/java/com/wdd/backend/exception/CurrencyPairDefinitionConflictException.java`
  - `develop/backend/src/main/java/com/wdd/backend/exception/ActiveCurrencyPairsExistException.java`
  - `develop/backend/src/main/java/com/wdd/backend/exception/GlobalExceptionHandler.java` (added handlers for the 3 new exceptions)
  - `develop/backend/src/test/java/com/wdd/backend/service/CurrencyPairDefinitionServiceTest.java`
  - `develop/backend/src/test/java/com/wdd/backend/controller/CurrencyPairDefinitionControllerTest.java`
- Notes:
  - Implemented full CRUD for `/api/currency-pair-definitions` following the existing Controller → Service → MyBatis Mapper (interface+XML) → DTO layering used by Brand/Currency, with no Lombok.
  - `CurrencyPairDefinition` persistence model carries `baseCurrencyCode`/`quoteCurrencyCode` as read-only join enrichment fields (populated by `CurrencyPairDefinitionMapper.xml`'s joins against `currency`), not real table columns.
  - Added a lightweight `CurrencyPair`/`CurrencyPairMapper` (insert, findByDefinitionId, findActiveBrandCodesByDefinitionId) owned by this spec purely to support the POST fan-out and the DELETE active-guard; the sibling brand-scoped `/api/currency-pairs` spec can extend this mapper/DTO rather than duplicate it.
  - POST fans out one `currency_pair` row per existing brand (`rate_type=AUTO`, `rate=NULL`, `active=false`) inside the same `@Transactional` method as the definition insert.
  - DELETE's 409 body uses a distinct shape (`{"error": ..., "activeBrandCodes": [...]}`) via a dedicated `ActiveCurrencyPairsExistException` handler, since it differs from the other endpoints' uniform `{"message": ...}` error shape.
  - `precision` column is MySQL's reserved word — backtick-quoted in all raw SQL (`` `precision` ``) in `CurrencyPairDefinitionMapper.xml`.
  - Verified against the live `wdd` database: confirmed `currency_pair_definition`/`currency_pair` schema (including `uk_currency_pair_definition`, `ck_currency_pair_definition_diff`, `ck_currency_pair_definition_precision`, and `ON DELETE CASCADE` from `currency_pair` to `currency_pair_definition`) matches the spec's expectations before implementing.
  - Added 17 service unit tests (Mockito) and 15 controller integration tests (live MySQL via `TestRestTemplate`, with `JdbcTemplate` used only to simulate the sibling API activating a brand pair for the DELETE-blocked scenario). Full suite: `mvn -f develop/backend/pom.xml test` → 72 tests, 0 failures, 0 errors. Confirmed both tables are left empty (0 rows) after the test run, i.e. cleanup is leak-free.
  - Did not implement `/api/currency-pairs` (brand-scoped currency pair CRUD) — explicitly out of scope per this spec's dispatch instructions; left for the sibling spec.
  - `docker/launch.json`/`.claude/launch.json` already correctly configured for the backend from prior spec execution; no changes needed.
