---
status: pending
title: "Currency Pair API"
requirement: "Provide REST API for currency pair CRUD (rate manual/auto), scoped per brand; lock currency code on update; block currency delete when referenced by a pair"
---

# Currency Pair API — Backend Spec

## Overview
Implement a REST API for managing currency pairs, each with an exchange rate that is either manually entered or automatically maintained, and each belonging to exactly one **brand**. Depends on the `currency_pair` table defined in `specs/dba/currency-pair.md`, the `brand` table/API (`specs/dba/brand.md`, `specs/backend/brand.md`), and the existing `currency` table/API (`specs/dba/currency.md`, `specs/backend/currency.md`).

This spec also requires two changes to the **existing** currency API (`pl.piomin.services.backend.*` currency classes) so the whole requirement is satisfied:
1. **Currency `code` becomes immutable after creation.** Remove `code` from `CurrencyUpdateRequest` entirely — it must not be possible to change a currency's code via `PUT /api/currencies/{id}`.
2. **Currency delete must be blocked while it is referenced by any currency pair.** Before deleting, `CurrencyService.delete` must check whether the currency is used as `base_currency_id` or `quote_currency_id` in `currency_pair`, and if so, reject with `409`.

## Requirements
- Full CRUD API for currency pairs
- Every currency pair belongs to exactly one brand (`brandId`), referencing the `brand` table (`specs/backend/brand.md`)
- `rate` field supports two modes via `rateType`: `MANUAL` (caller supplies the rate on every write) and `AUTO` (system-maintained; caller may still supply an initial/fallback value, but no automatic external rate-fetching job is in scope for this spec — that is future work)
- Base and quote currency must reference existing, distinct currencies
- Duplicate (brand, base, quote) pairs are rejected — the same (base, quote) combination is allowed under different brands
- Currency `code` is immutable once created (enforced on the existing currency update endpoint)
- Currency delete is rejected with `409` while referenced by any currency pair (enforced on the existing currency delete endpoint)

## API Contract

Base path: `/api/currency-pairs`

### 1. List Currency Pairs

```
GET /api/currency-pairs
```

Query parameters:
| Param   | Type    | Required | Description                |
|---------|---------|----------|------------------------------|
| brandId | Long    | No       | Filter by owning brand       |
| active  | Boolean | No       | Filter by active status      |

Response `200`:
```json
[
    {
        "id": 1,
        "brandId": 3,
        "brandCode": "PUG",
        "baseCurrencyId": 2,
        "baseCurrencyCode": "USD",
        "quoteCurrencyId": 1,
        "quoteCurrencyCode": "TWD",
        "rate": 32.5,
        "rateType": "MANUAL",
        "active": true,
        "createdAt": "2025-01-01T00:00:00",
        "updatedAt": "2025-01-01T00:00:00"
    }
]
```

### 2. Get Currency Pair by ID

```
GET /api/currency-pairs/{id}
```

Response `200`: single currency pair object (same shape as list item)

Response `404`:
```json
{
    "error": "Currency pair not found",
    "id": 999
}
```

### 3. Create Currency Pair

```
POST /api/currency-pairs
```

Request body:
```json
{
    "brandId": 3,
    "baseCurrencyId": 2,
    "quoteCurrencyId": 1,
    "rate": 32.5,
    "rateType": "MANUAL",
    "active": true
}
```

Validation:
| Field           | Rule                                                          |
|-----------------|-----------------------------------------------------------------|
| brandId         | Required, must reference an existing `brand.id`                 |
| baseCurrencyId  | Required, must reference an existing `currency.id`              |
| quoteCurrencyId | Required, must reference an existing `currency.id`, must differ from `baseCurrencyId` |
| rate            | Required, numeric, > 0                                          |
| rateType        | Required, one of `MANUAL`, `AUTO`                                |
| active          | Optional, defaults to true                                      |

Response `201`: created currency pair object with generated `id`

Response `404`: if `brandId`, `baseCurrencyId`, or `quoteCurrencyId` does not exist
```json
{
    "error": "Currency not found",
    "id": 999
}
```
```json
{
    "error": "Brand not found",
    "id": 999
}
```

Response `409`: if the (brand, base, quote) pair already exists
```json
{
    "error": "Currency pair already exists for this brand",
    "brandId": 3,
    "baseCurrencyId": 2,
    "quoteCurrencyId": 1
}
```

Response `400`: if `baseCurrencyId` equals `quoteCurrencyId`, or other validation failure
```json
{
    "error": "Base and quote currency must differ"
}
```

### 4. Update Currency Pair

```
PUT /api/currency-pairs/{id}
```

Request body: same shape as create. All fields optional (partial update). If `brandId`/`baseCurrencyId`/`quoteCurrencyId` are changed, re-validate existence, distinctness, and (brand, base, quote) uniqueness against other rows.

Response `200`: updated currency pair object

Response `404`: if `id`, `brandId`, `baseCurrencyId`, or `quoteCurrencyId` not found

Response `409`: if the resulting (brand, base, quote) pair collides with a different existing row

Response `400`: validation failure (e.g. `rate <= 0`, invalid `rateType`, base == quote)

### 5. Delete Currency Pair

```
DELETE /api/currency-pairs/{id}
```

Response `204`: no content

Response `404`: if id not found

## Implementation Details

### Layer Structure
Follow the existing currency feature's layering: Controller → Service → MyBatis Mapper (interface + XML), with request/response DTOs separate from the entity — same package structure as `pl.piomin.services.backend.{controller,service,mapper,model,dto,exception}`.

### Entity: `CurrencyPair`
Fields: `id`, `brandId`, `baseCurrencyId`, `quoteCurrencyId`, `rate` (`BigDecimal`), `rateType` (`String` or enum `RateType { MANUAL, AUTO }`), `active`, `createdAt`, `updatedAt`.

### Response enrichment
`CurrencyPairResponse` includes `brandCode` (joined from `brand`) and `baseCurrencyCode` / `quoteCurrencyCode` (joined from `currency`) in addition to the raw ids, so the frontend does not need extra lookups to render the table. Populate this via a mapper query that joins `currency_pair` to `brand` and to `currency` twice (aliased), rather than N+1 lookups.

### Service logic
- **Create**: validate brand existence (404 if missing), validate base/quote currency existence (404 if missing), validate base ≠ quote (400), check for existing pair with same (brand, base, quote) (409), insert.
- **Update**: same validations as create, scoped to "any row other than this id" for the uniqueness check.
- **Delete**: straightforward delete by id (no downstream references to check).
- **List/Get**: read-only, optional `brandId` and `active` filters on list.

### Required changes to the existing Currency feature
- `CurrencyUpdateRequest` (`develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyUpdateRequest.java`): remove the `code` field and its getter/setter entirely. `code` is set only once, at creation, via `CurrencyCreateRequest`.
- `CurrencyService.update` (`develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyService.java`): remove the code-mutation branch (lines handling `request.getCode()`), since the field no longer exists on the request DTO.
- `CurrencyService.delete`: before calling `currencyMapper.deleteById(id)`, call a new `CurrencyPairMapper.existsByCurrencyId(id)` (checks both `base_currency_id` and `quote_currency_id`); if true, throw a new `CurrencyInUseException` mapped to `409`:
```json
{
    "error": "Currency is referenced by one or more currency pairs and cannot be deleted",
    "id": 1
}
```
- `GlobalExceptionHandler`: add a handler for `CurrencyInUseException` → `409`, and for the new `CurrencyPairNotFoundException` / `CurrencyPairExistsException` → `404` / `409`, following the existing pattern used for `CurrencyNotFoundException` / `CurrencyCodeExistsException`.

### Error Handling
- Return `404` with JSON body when a currency pair, or a referenced currency, is not found
- Return `409` with JSON body when the unique (base, quote) constraint is violated, or when deleting a currency still in use
- Return `400` with field-level validation errors (base == quote, rate ≤ 0, invalid rateType)

## Acceptance Criteria
- [ ] `GET /api/currency-pairs` returns list of all pairs with brand/base/quote codes populated
- [ ] `GET /api/currency-pairs?brandId=3` filters correctly
- [ ] `GET /api/currency-pairs?active=true` filters correctly
- [ ] `GET /api/currency-pairs/{id}` returns single pair or 404
- [ ] `POST /api/currency-pairs` creates and returns 201
- [ ] `POST /api/currency-pairs` with base == quote returns 400
- [ ] `POST /api/currency-pairs` with nonexistent brand, base, or quote currency id returns 404
- [ ] `POST /api/currency-pairs` with duplicate (brand, base, quote) returns 409
- [ ] `POST /api/currency-pairs` with the same (base, quote) under a different brand succeeds (no false-positive 409)
- [ ] `PUT /api/currency-pairs/{id}` updates and returns 200
- [ ] `DELETE /api/currency-pairs/{id}` deletes and returns 204
- [ ] `PUT /api/currencies/{id}` no longer accepts/changes `code` (field removed from update DTO)
- [ ] `DELETE /api/currencies/{id}` returns 409 when the currency is referenced by a currency pair, and succeeds once no pair references it
- [ ] Unit tests for `CurrencyPairService` (positive and negative cases) and updated `CurrencyServiceTest` covering the immutable-code and delete-guard behavior
- [ ] Integration tests for `CurrencyPairController` endpoints and the updated currency delete/update endpoints
