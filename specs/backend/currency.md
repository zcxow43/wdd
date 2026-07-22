---
status: pending
title: "Currency API"
requirement: "Provide REST API for currency CRUD operations"
---

# Currency API — Backend Spec

## Overview
Implement a REST API for managing currencies. Provides list, get-by-id, create, update, and delete endpoints. Depends on the `currency` table defined in `specs/dba/currency.md`.

## Requirements
- Full CRUD API for currencies
- List endpoint supports optional filtering by `active` status
- Validation on create/update inputs
- Proper error responses for not-found and validation failures

## API Contract

Base path: `/api/currencies`

### 1. List Currencies

```
GET /api/currencies
```

Query parameters:
| Param  | Type    | Required | Description                    |
|--------|---------|----------|--------------------------------|
| active | Boolean | No       | Filter by active status        |

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
        "active": true,
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
    "decimalPlaces": 0,
    "active": true
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
| active        | Optional, defaults to true                |

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
Fields map 1:1 to the `currency` table columns. Use camelCase in Java (`nameZh`, `decimalPlaces`, `createdAt`, `updatedAt`).

### Error Handling
- Return `404` with JSON body when entity not found
- Return `409` with JSON body when unique constraint violated on create
- Return `400` with field-level validation errors

## Acceptance Criteria
- [ ] `GET /api/currencies` returns list of all currencies
- [ ] `GET /api/currencies?active=true` filters correctly
- [ ] `GET /api/currencies/{id}` returns single currency or 404
- [ ] `POST /api/currencies` creates and returns 201
- [ ] `POST /api/currencies` with duplicate code returns 409
- [ ] `PUT /api/currencies/{id}` updates and returns 200
- [ ] `DELETE /api/currencies/{id}` deletes and returns 204
- [ ] Validation errors return 400 with details
- [ ] Unit tests for service layer (positive and negative cases)
- [ ] Integration tests for controller endpoints
