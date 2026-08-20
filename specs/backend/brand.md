---
status: done
title: "Brand API"
requirement: "匯率中心需要品牌主檔 API，內建七個品牌 au, moneta, pug, star, um, vjp, vt，只允許開啟/關閉品牌"
depends_on: []
---

# Brand — Backend Spec

## Overview
Brand is the ownership root for every brand-scoped configuration in the exchange rate center. This spec exposes the seven seeded brands (`au`, `moneta`, `pug`, `star`, `um`, `vjp`, `vt` — see [brand.md](../dba/brand.md)) for listing and lets an admin toggle a brand's `active` flag. There is no create/delete API — brands are seeded only, so `code` and `name` are immutable through the API.

## Requirements

### Entity: Brand
| Field | Type | Rule |
|---|---|---|
| id | Long | PK |
| code | String | Immutable; seeded only |
| name | String | Immutable; seeded only |
| active | Boolean | Only field mutable via API |
| createdAt / updatedAt | Timestamp | System maintained |

### API Contract

**GET /api/brands**
- Query param: `active` (optional boolean) — when present, filter to brands with that `active` value; when absent, return all brands.
- Response `200`: `[ { "id": 1, "code": "au", "name": "au", "active": true, "createdAt": "...", "updatedAt": "..." }, ... ]`

**GET /api/brands/{id}**
- Response `200`: single Brand object (same shape as above).
- Not found → `404`.

**PUT /api/brands/{id}**
- Request body: `{ "active": true }` — `active` is the only accepted field.
- Missing/null `active` → `400`.
- `id` not found → `404`.
- Response `200`: the updated Brand object.

No `POST /api/brands` or `DELETE /api/brands/{id}` — brands are seeded only, not created/removed through the API.

## Implementation Details
1. `GET /api/brands` reads live table directly, applying the optional `active` filter at the query level.
2. `GET /api/brands/{id}` reads live table directly; throw a not-found exception mapped to `404` when no row matches.
3. `PUT /api/brands/{id}`: load existing row (404 if missing) → validate request body has non-null `active` (400 if missing) → update only the `active` column → return the refreshed row. This mutation applies directly; it does not go through an audit/approval flow.

## Acceptance Criteria
- [x] `GET /api/brands` with no query param returns all 7 seeded brands.
- [x] `GET /api/brands?active=true` returns only active brands; `?active=false` returns only inactive ones.
- [x] `GET /api/brands/{id}` returns `404` for a non-existent id.
- [x] `PUT /api/brands/{id}` with `{"active": false}` flips the brand to inactive and persists it.
- [x] `PUT /api/brands/{id}` with a body missing `active` returns `400`.
- [x] `PUT /api/brands/{id}` for a non-existent id returns `404`.
- [x] No endpoint allows creating or deleting a brand.

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/backend/src/main/java/com/wdd/backend/dto/Brand.java` (new — MyBatis persistence model for the `brand` table)
  - `develop/backend/src/main/java/com/wdd/backend/dto/BrandResponse.java` (new — API response DTO)
  - `develop/backend/src/main/java/com/wdd/backend/dto/BrandUpdateRequest.java` (new — PUT request body DTO, single `active` field)
  - `develop/backend/src/main/java/com/wdd/backend/mapper/BrandMapper.java` (new — MyBatis mapper interface: `findAll`, `findById`, `updateActive`)
  - `develop/backend/src/main/resources/mapper/BrandMapper.xml` (new — MyBatis SQL: dynamic `active` filter on list query, id lookup, single-column active update)
  - `develop/backend/src/main/java/com/wdd/backend/service/BrandService.java` (new — validation, 404/400 exception raising, `@Transactional` update)
  - `develop/backend/src/main/java/com/wdd/backend/controller/BrandController.java` (new — `GET /api/brands`, `GET /api/brands/{id}`, `PUT /api/brands/{id}`; no POST/DELETE mappings exist)
  - `develop/backend/src/main/java/com/wdd/backend/exception/BrandNotFoundException.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/exception/InvalidRequestException.java` (new)
  - `develop/backend/src/main/java/com/wdd/backend/exception/GlobalExceptionHandler.java` (new — `@RestControllerAdvice` mapping `BrandNotFoundException` → 404, `InvalidRequestException` → 400)
  - `develop/backend/src/test/java/com/wdd/backend/controller/BrandControllerTest.java` (new — 8 integration tests via `TestRestTemplate` against the live `wdd` MySQL DB, covering list/filter/get/put/404/400 cases; restores the `vt` brand's original `active` value in `@AfterEach` so tests don't leave side effects on the seeded data)
  - `develop/backend/src/test/java/com/wdd/backend/service/BrandServiceTest.java` (new — 5 unit tests with a mocked `BrandMapper` covering not-found and missing-`active` validation paths)
- Notes: Implemented the standard controller → service → mapper (MyBatis, interface + XML) layered architecture, matching the existing `HealthController`/skeleton conventions (no Lombok, explicit getters/setters). `GET /api/brands` supports the optional `active` query param, filtered at the SQL level via a MyBatis `<if>`. `PUT /api/brands/{id}` validates `active` is non-null (400 via `InvalidRequestException`), checks existence (404 via `BrandNotFoundException`), updates only the `active` column, then re-reads and returns the refreshed row — no audit/approval flow, per spec. No `POST`/`DELETE` mappings exist for `/api/brands`.
  Verified with: (1) `mvn -f develop/backend/pom.xml compile` — success; (2) `mvn -f develop/backend/pom.xml test` — all 14 tests pass (`BrandControllerTest`: 8/8, `BrandServiceTest`: 5/5, `HealthControllerTest`: 1/1), confirmed via `target/surefire-reports/*.txt`; (3) live manual smoke test — started `mvn spring-boot:run` on port 8080, exercised every endpoint with `curl` against the live MySQL `wdd.brand` table: full list returns exactly the 7 seeded codes (`au, moneta, pug, star, um, vjp, vt`) all active; `?active=false` returns `[]` before any mutation; `GET /api/brands/1` returns the `au` row; `GET /api/brands/999999` → `404`; `PUT` with `{}` → `400`; `PUT` on id `999999` → `404`; `PUT /api/brands/7 {"active":false}` flips and persists (`updated_at` bumped, confirmed via follow-up `GET`), then restored to `true`; `POST /api/brands` and `DELETE /api/brands/1` both → `405 Method Not Allowed` (no handler registered), confirming brands cannot be created or deleted through the API. Confirmed via direct `mysql` query that all 7 brands ended the session with `active = 1` (no residual test side effects). Server process stopped cleanly afterward and port 8080 verified free.
