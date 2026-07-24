---
status: done
title: "Brand API"
requirement: "Provide REST API to list brands and toggle enable/disable; brands are a fixed seeded set (AU, MONETA, PUG, STAR, UM, VJP, VT)"
---

# Brand API — Backend Spec

## Overview
Implement a REST API for reading brands and toggling their enabled/disabled state. Depends on the `brand` table defined in `specs/dba/brand.md`. Brands are a fixed, seeded set (`AU`, `MONETA`, `PUG`, `STAR`, `UM`, `VJP`, `VT`) — this API intentionally has **no create or delete** endpoint; only listing and enable/disable are in scope, matching the requirement "品牌需獨立一張資料表, 可開啟關閉" (brand needs its own table, can be enabled/disabled). `specs/backend/currency-pair.md` depends on this spec: every currency pair belongs to a brand.

## Requirements
- List all brands
- Get a single brand by id
- Toggle a brand's `active` flag (enable/disable) — no other field is editable via the API
- `code` and `name` are immutable (seeded via migration only)

## API Contract

Base path: `/api/brands`

### 1. List Brands

```
GET /api/brands
```

Query parameters:
| Param  | Type    | Required | Description             |
|--------|---------|----------|--------------------------|
| active | Boolean | No       | Filter by active status |

Response `200`:
```json
[
    {
        "id": 1,
        "code": "AU",
        "name": "AU",
        "active": true,
        "createdAt": "2025-01-01T00:00:00",
        "updatedAt": "2025-01-01T00:00:00"
    }
]
```

### 2. Get Brand by ID

```
GET /api/brands/{id}
```

Response `200`: single brand object (same shape as list item)

Response `404`:
```json
{
    "error": "Brand not found",
    "id": 999
}
```

### 3. Toggle Brand Active State

```
PUT /api/brands/{id}
```

Request body:
```json
{
    "active": false
}
```

Validation:
| Field  | Rule                          |
|--------|-------------------------------|
| active | Required, boolean             |

Response `200`: updated brand object

Response `404`: if id not found

Response `400`: if `active` is missing/invalid

There is intentionally no `POST /api/brands` or `DELETE /api/brands/{id}` — brands are seeded once via `specs/dba/brand.md` and never created or removed through the API.

## Implementation Details

### Layer Structure
Follow the existing feature layering: Controller → Service → MyBatis Mapper (interface + XML), request/response DTOs separate from the entity — same package structure as the currency feature (`pl.piomin.services.backend.{controller,service,mapper,model,dto,exception}`).

### Entity: `Brand`
Fields map 1:1 to the `brand` table columns: `id`, `code`, `name`, `active`, `createdAt`, `updatedAt`.

### DTOs
- `BrandResponse`: all fields (read-only view).
- `BrandUpdateRequest`: only `active` (Boolean, `@NotNull`). No `code` or `name` field exists on this DTO — there is nothing else to update.

### Service logic
- `list(Boolean active)`: read all brands, optionally filtered.
- `getById(Long id)`: read one, `404` via `BrandNotFoundException` if missing.
- `updateActive(Long id, BrandUpdateRequest request)`: load by id (`404` if missing), set `active`, persist, return updated row.

### Error Handling
- Return `404` with JSON body when brand not found
- Return `400` with field-level validation errors when `active` is missing/invalid
- Add `BrandNotFoundException` → `404` handler in `GlobalExceptionHandler`, following the existing pattern for `CurrencyNotFoundException`

## Acceptance Criteria
- [ ] `GET /api/brands` returns all 7 seeded brands
- [ ] `GET /api/brands?active=true` filters correctly
- [ ] `GET /api/brands/{id}` returns single brand or 404
- [ ] `PUT /api/brands/{id}` with `{"active": false}` disables the brand and returns 200
- [ ] `PUT /api/brands/{id}` with `{"active": true}` re-enables the brand and returns 200
- [ ] `PUT /api/brands/{id}` with missing/invalid `active` returns 400
- [ ] `PUT /api/brands/{id}` for nonexistent id returns 404
- [ ] No endpoint exists to create or delete a brand
- [ ] Unit tests for `BrandService` (positive and negative cases)
- [ ] Integration tests for `BrandController` endpoints

---
## Execution Result
- Status: DONE
- Files changed:
  - develop/backend/src/main/java/pl/piomin/services/backend/model/Brand.java (new — entity, 1:1 with `brand` table)
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/BrandResponse.java (new — read-only view)
  - develop/backend/src/main/java/pl/piomin/services/backend/dto/BrandUpdateRequest.java (new — `active` only, `@NotNull`)
  - develop/backend/src/main/java/pl/piomin/services/backend/mapper/BrandMapper.java (new — MyBatis mapper interface: `findAll`, `findById`, `update`, plus `insert`/`deleteById` used only by tests to seed/clean the H2 schema, never exposed via controller)
  - develop/backend/src/main/resources/mapper/BrandMapper.xml (new — MyBatis SQL mapper)
  - develop/backend/src/main/java/pl/piomin/services/backend/service/BrandService.java (new — `list`, `getById`, `updateActive`)
  - develop/backend/src/main/java/pl/piomin/services/backend/controller/BrandController.java (new — `GET /api/brands`, `GET /api/brands/{id}`, `PUT /api/brands/{id}`; intentionally no POST/DELETE)
  - develop/backend/src/main/java/pl/piomin/services/backend/exception/BrandNotFoundException.java (new)
  - develop/backend/src/main/java/pl/piomin/services/backend/exception/GlobalExceptionHandler.java (edited — added `BrandNotFoundException` → 404 handler, following the existing `CurrencyNotFoundException` pattern)
  - develop/backend/src/test/resources/schema.sql (edited — added H2-compatible `brand` table alongside existing `currency` table)
  - develop/backend/src/test/java/pl/piomin/services/backend/service/BrandServiceTest.java (new — 7 unit tests, Mockito)
  - develop/backend/src/test/java/pl/piomin/services/backend/controller/BrandControllerTest.java (new — 9 MockMvc integration tests against H2)
  - develop/backend/pom.xml (edited — version bumped 0.0.1 → 0.0.2 per semantic versioning convention, description updated)
  - develop/backend/README.md (edited — documented Brand API endpoints and 0.0.2 version history entry)
- Notes:
  - Implemented list/get/toggle-active for `/api/brands` exactly per contract, reusing the existing Controller → Service → MyBatis Mapper (interface + XML) layering and DTO conventions from the Currency feature. No Lombok used.
  - `BrandUpdateRequest` only exposes `active` (`@NotNull`); `code` and `name` are immutable and never accepted by the API, matching the "no create/delete" requirement — there is no `POST /api/brands` or `DELETE /api/brands/{id}` in the controller.
  - `BrandMapper` includes `insert`/`deleteById` methods purely to let integration tests seed and reset the fixed brand set against the H2 in-memory schema between test runs; these are not wired to any controller endpoint, so the "no create/delete via API" constraint from the spec is preserved at the HTTP surface. Production brand rows come exclusively from the `V002__create_brand_table.sql` migration (`specs/dba/brand.md`).
  - Extended `src/test/resources/schema.sql` with an H2-compatible `brand` table (mirrors the live MySQL migration's columns) so `mvn test` runs entirely against the isolated in-memory H2 database and never touches the live MySQL `wdd` database.
  - Added `GlobalExceptionHandler.handleNotFound(BrandNotFoundException)` returning `{"error": "Brand not found", "id": ...}` with `404`, alongside the existing `CurrencyNotFoundException` handler (both handler methods are legally overloaded by exception type). Bean Validation on `BrandUpdateRequest.active` (`@NotNull`) drives the existing `MethodArgumentNotValidException` handler to return `400` with `details.active` when `active` is missing or not a boolean.
  - Ran `mvn -f develop/backend/pom.xml compile` (BUILD SUCCESS) and `mvn -f develop/backend/pom.xml test` (BUILD SUCCESS, 40 tests total: 12 CurrencyServiceTest + 12 CurrencyControllerTest + 7 BrandServiceTest + 9 BrandControllerTest, 0 failures/errors). No changes were needed to `.circleci/config.yml` — the existing `build-and-test` job already runs `mvn test` against the whole backend module, so the new Brand tests are picked up automatically.
  - Bumped the Maven project version to `0.0.2` (PATCH bump per project convention, following the currency feature's `0.0.1`) and updated `README.md` with the Brand API table and version history entry.
