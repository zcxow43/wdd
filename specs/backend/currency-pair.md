---
status: done
title: "Currency Pair API (Brand-Scoped)"
requirement: "品牌幣種對可以 CRUD，設定自動匯率或手動匯率，可以開啟關閉，手動匯率必須填上匯率，精度受幣種對限制"
depends_on: [currency-pair-definition, brand]
---

# Currency Pair — Backend Spec

## Overview
CRUD for each brand's own currency pair settings (see [currency-pair.md](../dba/currency-pair.md)). Most rows are created by [currency-pair-definition.md](currency-pair-definition.md)'s fan-out when a definition is created, but this API also supports creating/deleting individual rows directly (e.g. to recreate one that was deleted). A pair's rate is either `AUTO` (system-derived elsewhere, stored as `null` here) or `MANUAL` (an admin-entered value, required and validated against the parent definition's `precision`).

## Requirements

### Entity: CurrencyPair
| Field | Type | Rule |
|---|---|---|
| id | Long | PK |
| currencyPairDefinitionId | Long | Required on create; must reference an existing definition; immutable after creation |
| baseCurrencyCode / quoteCurrencyCode | String | Read-only enrichment (joined via the definition) |
| brandId | Long | Required on create; must reference an existing brand; immutable after creation |
| brandCode | String | Read-only enrichment (joined from `brand.code`) |
| rateType | String | `AUTO` or `MANUAL`; defaults to `AUTO` if omitted |
| rate | BigDecimal | Required (and must be `> 0`) when `rateType` is `MANUAL`; forced to `null` when `rateType` is `AUTO` regardless of what's sent; when `MANUAL`, decimal places must not exceed the parent definition's `precision` |
| active | Boolean | Defaults to `false`; freely togglable |
| createdAt / updatedAt | Timestamp | System maintained |

### API Contract

**GET /api/currency-pairs**
- Query params (all optional): `currencyPairDefinitionId`, `brandId`, `active` (boolean) — filter when present.
- Response `200`: `[ { "id": 10, "currencyPairDefinitionId": 1, "baseCurrencyCode": "USD", "quoteCurrencyCode": "JPY", "brandId": 1, "brandCode": "au", "rateType": "AUTO", "rate": null, "active": false, "createdAt": "...", "updatedAt": "..." }, ... ]`

**GET /api/currency-pairs/{id}**
- Response `200`: single object (same shape). Not found → `404`.

**POST /api/currency-pairs**
- Request body: `{ "currencyPairDefinitionId": 1, "brandId": 1, "rateType": "MANUAL", "rate": 150.25, "active": false }` (`rateType` defaults `"AUTO"`, `active` defaults `false` if omitted).
- Validation: `currencyPairDefinitionId` must reference an existing definition, `brandId` must reference an existing brand (`400` if either doesn't exist); the `(currencyPairDefinitionId, brandId)` combination must not already exist (`409` if it does); `rateType` must be `AUTO`/`MANUAL`; if `MANUAL`, `rate` is required, `> 0`, and its decimal places must not exceed the definition's `precision` (`400` on any violation). If `AUTO`, any `rate` sent is ignored and stored as `null`.
- Response `201`: the created object.

**PUT /api/currency-pairs/{id}**
- Request body: any subset of `{ "rateType": "MANUAL", "rate": 150.25, "active": true }` — `currencyPairDefinitionId`/`brandId` are immutable and ignored if sent. Fields not present keep their current value.
- Validation: same rate/precision rules as create, applied to the resulting `rateType`+`rate` combination — `400` on violation.
- Not found → `404`.
- Response `200`: the updated object.

**DELETE /api/currency-pairs/{id}**
- Not found → `404`.
- Response `204` on success, row removed. No guard — a brand currency pair can be deleted regardless of its `active` state (the guard lives on the parent definition's delete, not here).

## Implementation Details
1. `GET` endpoints read the live table directly, joining `brand` and (via the definition) `currency` for the enrichment fields, applying the optional filters at the query level.
2. `POST`: validate `currencyPairDefinitionId`/`brandId` exist (400) → check `(definition, brand)` uniqueness (409) → validate `rateType`/`rate` against the definition's `precision` (400) → insert → `201`.
3. `PUT`: load existing row (404 if missing) → merge the request's fields onto the current values → validate the resulting `rateType`/`rate` combination against the parent definition's `precision` (400) → update → `200`.
4. `DELETE`: load existing row (404 if missing) → delete → `204`.
5. Rate/precision validation reads the parent `currency_pair_definition.precision` (via `currencyPairDefinitionId`, unchanged across the row's lifetime) — reuse the definition lookup already required for validation, don't duplicate the query.
6. All mutations apply directly; none of this goes through an audit/approval flow.

## Acceptance Criteria
- [x] `GET /api/currency-pairs` returns all pairs; `currencyPairDefinitionId`/`brandId`/`active` filters narrow the results correctly.
- [x] `GET /api/currency-pairs/{id}` returns `404` for a non-existent id.
- [x] `POST` with `rateType: "AUTO"` creates a row with `rate: null` even if a `rate` value was sent in the body.
- [x] `POST` with `rateType: "MANUAL"` and no `rate` (or `rate <= 0`) returns `400`.
- [x] `POST` with `rateType: "MANUAL"` and a `rate` with more decimal places than the definition's `precision` returns `400`.
- [x] `POST` with a duplicate `(currencyPairDefinitionId, brandId)` returns `409`.
- [x] `POST` with a non-existent `currencyPairDefinitionId` or `brandId` returns `400`.
- [x] `PUT` can toggle `active` independently of `rateType`/`rate`.
- [x] `PUT` switching `rateType` from `MANUAL` to `AUTO` clears `rate` to `null`.
- [x] `PUT`/`DELETE` for a non-existent id return `404`.
- [x] `DELETE` succeeds regardless of the row's `active` value (no guard at this level).

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPair.java` (extended: added `baseCurrencyCode`/`quoteCurrencyCode` fields)
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairResponse.java` (extended: added `baseCurrencyCode`/`quoteCurrencyCode` fields; constructor signature updated)
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairCreateRequest.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/dto/CurrencyPairUpdateRequest.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/mapper/CurrencyPairMapper.java` (extended: added `findAll`, `findById`, `findByDefinitionAndBrand`, `update`, `deleteById`)
  - `develop/backend/src/main/resources/mapper/CurrencyPairMapper.xml` (extended: shared `selectColumns` SQL fragment joining `brand` and, via `currency_pair_definition`, `currency` twice for base/quote codes; added `findAll` with optional filters, `findById`, `findByDefinitionAndBrand`, `update`, `deleteById`)
  - `develop/backend/src/main/java/com/wdd/backend/exception/CurrencyPairNotFoundException.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/exception/CurrencyPairConflictException.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/exception/GlobalExceptionHandler.java` (extended: handlers for the two new exceptions, 404/409)
  - `develop/backend/src/main/java/com/wdd/backend/service/CurrencyPairService.java` (new: findAll/findById/create/update/delete + rate/precision validation)
  - `develop/backend/src/main/java/com/wdd/backend/service/CurrencyPairDefinitionService.java` (updated `toCurrencyPairResponse` call site for the new `CurrencyPairResponse` constructor shape)
  - `develop/backend/src/main/java/com/wdd/backend/controller/CurrencyPairController.java` (new: `GET/POST /api/currency-pairs`, `GET/PUT/DELETE /api/currency-pairs/{id}`)
  - `develop/backend/src/test/java/com/wdd/backend/service/CurrencyPairServiceTest.java` (new, 19 unit tests, mocked mappers)
  - `develop/backend/src/test/java/com/wdd/backend/controller/CurrencyPairControllerTest.java` (new, 13 integration tests, live DB via `TestRestTemplate`)
- Notes:
  - Extended the sibling `currency-pair-definition` spec's lightweight `CurrencyPair`/`CurrencyPairResponse`/`CurrencyPairMapper` starting point rather than creating parallel classes, per instructions.
  - `findByDefinitionId` (used by the definition's fan-out response) now also returns `baseCurrencyCode`/`quoteCurrencyCode` via the same joins — purely additive, verified the full existing suite (`CurrencyPairDefinitionServiceTest`/`CurrencyPairDefinitionControllerTest`) still passes unmodified.
  - Rate precision check uses `rate.stripTrailingZeros().scale()` (floored at 0) so trailing zeros in a submitted rate (e.g. `150.2500` against `precision: 2`) don't cause a false-positive rejection.
  - `PUT` merge semantics: any field omitted (`null`) from the request body keeps the row's current value; when the resulting `rateType` is `AUTO`, `rate` is always forced to `null` regardless of what was sent or previously stored.
  - Verified with `mvn -f develop/backend/pom.xml compile` and `mvn -f develop/backend/pom.xml test` — full suite green: 104 tests, 0 failures, 0 errors, 0 skipped (includes the 19 new service unit tests and 13 new controller integration tests, plus all pre-existing tests with no regressions).
  - Additionally ran a live smoke test against the real MySQL-backed app (`mvn spring-boot:run` on port 8080): created currencies + a `precision: 2` definition, then exercised `GET /api/currency-pairs` (list + `currencyPairDefinitionId`/`brandId`/`active` filters), `GET /{id}`, `POST` (AUTO ignoring a sent rate, MANUAL success, MANUAL exceeding precision → 400, duplicate `(definitionId, brandId)` → 409, non-existent definition/brand → 400), `PUT` (toggle `active` alone, `MANUAL`→`AUTO` clearing `rate`, over-precision rate → 400, unknown id → 404), and `DELETE` (unknown id → 404; success on an `active: true` row with no guard) — all responses matched the spec's contract exactly. All smoke-test rows were cleaned up (currency pairs deactivated, definition deleted cascading its fanned-out pairs, both test currencies deleted) and the server process was stopped afterward.
