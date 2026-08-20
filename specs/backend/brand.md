---
status: pending
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
- [ ] `GET /api/brands` with no query param returns all 7 seeded brands.
- [ ] `GET /api/brands?active=true` returns only active brands; `?active=false` returns only inactive ones.
- [ ] `GET /api/brands/{id}` returns `404` for a non-existent id.
- [ ] `PUT /api/brands/{id}` with `{"active": false}` flips the brand to inactive and persists it.
- [ ] `PUT /api/brands/{id}` with a body missing `active` returns `400`.
- [ ] `PUT /api/brands/{id}` for a non-existent id returns `404`.
- [ ] No endpoint allows creating or deleting a brand.
