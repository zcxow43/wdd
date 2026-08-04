---
status: done
title: "Spread (點差) API"
requirement: "每個品牌幣種對可以配置點差, 點差分為預設點差或客制點差, 有入金出金兩個欄位; 客制點差可將多個幣種對加入同一組, 每個幣種對最多屬於一組客制點差; 未配置的幣種對使用該品牌的預設點差; 配置完後可以隨意 CRUD; 點差依品牌區分; 點差也需要加入審核功能"
depends_on: [brand, currency-pair, audit]
---

# Spread (點差) API — Backend Spec

## Overview
Implements REST APIs for the two spread concepts described in `specs/dba/spread-default.md`, `specs/dba/spread-group.md`, and `specs/dba/spread-group-member.md`:
1. **Default spread** (`spread_default`) — one row per brand, read + update only (no create/delete; mirrors the "fixed set, no create/delete" pattern already used by `specs/backend/brand.md`, but as its own table so `Brand`/`BrandController` are not touched).
2. **Custom spread groups** (`spread_group` + `spread_group_member`) — full CRUD per brand. Multiple currency pairs can be added to the same group; a currency pair may belong to at most one group at a time. Assigning a pair to a group it isn't currently in **moves** it there (removing any prior membership) rather than erroring — this is what lets the UI "drag" a pair from one group into another.

**Every mutation on either concept goes through the existing generic audit module** (`specs/backend/audit.md`) instead of applying directly — updating the default spread, and creating/updating/deleting a spread group, all submit a `PENDING` audit request and only take effect once approved, exactly like `currency-pair`'s create/update/delete (`specs/backend/currency-pair-approval.md` is the reference implementation to mirror). `GET` endpoints are unaffected and keep reading live, already-approved rows directly.

Depends on `Brand` (existing), `CurrencyPair` (existing), and the generic `AuditHandler`/`AuditService`/`/api/audit-requests` module (`specs/backend/audit.md`, already implemented) — implement/extend that first if not already present. Cross-referenced by `specs/frontend/spread.md`.

## Requirements
- `GET` for `spread_default`, scoped per brand; `PUT` submits an audit request instead of updating directly — no create/delete.
- `GET` for `spread_group` (list/by-id), scoped per brand; `POST`/`PUT`/`DELETE` each submit an audit request instead of mutating directly.
- A `spread_group`'s membership (its set of `currencyPairId`s) is proposed as part of the create/update request's snapshot — no separate membership endpoints.
- A currency pair passed as a member must belong to the same brand as the group.
- A currency pair can only ever be a member of one group; a proposal that assigns it to a new group is not rejected for that reason — approving it will detach the pair from whichever group (if any) it was previously in.
- A resolver endpoint that, given a `currencyPairId`, returns whichever spread currently applies to it (its group's spread if it has one, otherwise its brand's default spread) — reads live, approved data; unaffected by the audit workflow.
- Approving a deletion of a `spread_group` removes its memberships; those pairs immediately fall back to the default spread.
- A `SpreadDefaultAuditHandler` (`entityType = "SPREAD_DEFAULT"`) and a `SpreadGroupAuditHandler` (`entityType = "SPREAD_GROUP"`) each implement the generic `AuditHandler` interface (`specs/backend/audit.md`) — the audit module itself is not modified.

## `SpreadDefaultAuditHandler` snapshot shape
`before_snapshot`/`after_snapshot` for `entityType = "SPREAD_DEFAULT"` (only ever submitted as `UPDATE` — a `spread_default` row is never created/deleted through the API):
```json
{ "brandId": 1, "brandCode": "AU", "depositSpread": 0.1, "withdrawSpread": 0.2 }
```

## `SpreadGroupAuditHandler` snapshot shape
`before_snapshot`/`after_snapshot` for `entityType = "SPREAD_GROUP"`:
```json
{
  "brandId": 1, "brandCode": "AU",
  "name": "Group A",
  "depositSpread": 0.1, "withdrawSpread": 0.2,
  "currencyPairIds": [3, 4],
  "members": [
    { "currencyPairId": 3, "baseCurrencyCode": "USD", "quoteCurrencyCode": "JPY" },
    { "currencyPairId": 4, "baseCurrencyCode": "USD", "quoteCurrencyCode": "EUR" }
  ]
}
```
`currencyPairIds` is the authoritative field `apply(...)` acts on; `members` is enrichment carried along purely so the audit review UI can render pair codes without a second lookup (mirrors `brandCode`/`baseCurrencyCode`/`quoteCurrencyCode` enrichment in `CurrencyPairAuditHandler`, `specs/backend/currency-pair-approval.md`).

## API Contract

New controller: `SpreadController`.

### Default Spread — base path `/api/spread-defaults`

#### 1. List Default Spreads (unaffected by audit workflow)
```
GET /api/spread-defaults?brandId={id}
```
Query parameters:
| Param   | Type | Required | Description        |
|---------|------|----------|---------------------|
| brandId | Long | No       | Filter by brand     |

Response `200`:
```json
[
  {
    "id": 1,
    "brandId": 1,
    "brandCode": "AU",
    "depositSpread": 0,
    "withdrawSpread": 0,
    "createdAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00"
  }
]
```

#### 2. Get Default Spread by ID (unaffected by audit workflow)
```
GET /api/spread-defaults/{id}
```
Response `200`: single object (same shape). Response `404` if not found: `{"error": "Spread default not found", "id": 999}`.

#### 3. Submit Default Spread Update
```
PUT /api/spread-defaults/{id}
```
Request body:
```json
{ "depositSpread": 0.1, "withdrawSpread": 0.2, "requestedBy": "Alice" }
```
`requestedBy` optional (no auth system, free-text, matching `specs/backend/currency-pair-approval.md`'s convention).

Validation: `depositSpread`/`withdrawSpread` both required, numeric, `>= 0`.

Behavior: `SpreadController.updateDefault` loads the target row's current `brandId`/`brandCode` (for the `before` snapshot's context — actually delegated to `AuditService.submit`, which calls `handler.snapshotOf(id)`), builds the proposed `after` map, and calls `auditService.submit("SPREAD_DEFAULT", UPDATE, id, after, requestedBy)`. Nothing is persisted to `spread_default` directly.

Response **`202 Accepted`**: `AuditRequestResponse` with `entityType: "SPREAD_DEFAULT"`, `actionType: "UPDATE"`, `entityId: id`, `status: "PENDING"`, `before: <row's current values>`, `after: <proposed values>`.

Response `404`: `id` not found. Response `400`: validation failure. Response `409`: a `PENDING` request already exists for this `spread_default` row — `{"error": "A pending audit request already exists for this entity"}` (the generic dedup message from `specs/backend/audit.md`).

There is intentionally no `POST`/`DELETE` — one `spread_default` row exists per brand from the moment that brand is seeded (`specs/dba/spread-default.md`), and it is never created or removed through the API.

### Custom Spread Groups — base path `/api/spread-groups`

#### 4. List Spread Groups (unaffected by audit workflow)
```
GET /api/spread-groups?brandId={id}
```
Query parameters:
| Param   | Type | Required | Description    |
|---------|------|----------|-----------------|
| brandId | Long | No       | Filter by brand |

Response `200`:
```json
[
  {
    "id": 10,
    "brandId": 1,
    "brandCode": "AU",
    "name": "Group A",
    "depositSpread": 0.1,
    "withdrawSpread": 0.2,
    "members": [
      { "currencyPairId": 3, "baseCurrencyCode": "USD", "quoteCurrencyCode": "JPY" },
      { "currencyPairId": 4, "baseCurrencyCode": "USD", "quoteCurrencyCode": "EUR" }
    ],
    "createdAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00"
  }
]
```

#### 5. Get Spread Group by ID (unaffected by audit workflow)
```
GET /api/spread-groups/{id}
```
Response `200`: single object (same shape as list item, with `members`). Response `404` if not found.

#### 6. Submit Spread Group Create
```
POST /api/spread-groups
```
Request body:
```json
{
  "brandId": 1,
  "name": "Group A",
  "depositSpread": 0.1,
  "withdrawSpread": 0.2,
  "currencyPairIds": [3, 4],
  "requestedBy": "Alice"
}
```
Validation:
| Field           | Rule                                                                 |
|-----------------|-----------------------------------------------------------------------|
| brandId         | Required; brand must exist                                            |
| name            | Required, non-blank, max 100 chars; unique within the brand           |
| depositSpread   | Required, numeric, `>= 0`                                             |
| withdrawSpread  | Required, numeric, `>= 0`                                             |
| currencyPairIds | Optional (defaults to empty list); no duplicate ids within the array; each id must reference an existing currency pair belonging to `brandId` |
| requestedBy     | Optional                                                               |

Behavior: `SpreadController.createGroup` builds the proposed `after` map (including resolved `brandCode`/`members` enrichment) and calls `auditService.submit("SPREAD_GROUP", CREATE, null, after, requestedBy)`. Nothing is inserted into `spread_group`/`spread_group_member`.

Response **`202 Accepted`**: `AuditRequestResponse` with `entityType: "SPREAD_GROUP"`, `actionType: "CREATE"`, `entityId: null`, `status: "PENDING"`, `before: null`, `after: <submitted values, enriched>`.

Response `400`: validation failure, duplicate id within `currencyPairIds`, or a `currencyPairId` whose `brandId` doesn't match the group's `brandId`.
Response `404`: `brandId` or a `currencyPairId` doesn't exist.
Response `409`: `name` already used by another **live** group in the same brand, **or** a `PENDING` `SPREAD_GROUP`/`CREATE` request already exists for the same `(brandId, name)` — `{"error": "A pending create request already exists for this brand/name combination"}` (mirrors `specs/backend/currency-pair-approval.md`'s own CREATE natural-key dedup, since `entityId` doesn't exist yet for the generic dedup check to key on).

A `currencyPairId` in the request that is currently a member of a *different* group is **not** rejected — approving this request will move it (see `apply(...)` below).

#### 7. Submit Spread Group Update
```
PUT /api/spread-groups/{id}
```
Request body (all fields optional except `requestedBy`'s usual optionality — only provided fields change, same partial-update convention as `CurrencyPairUpdateRequest`):
```json
{
  "name": "Group A Renamed",
  "depositSpread": 0.15,
  "withdrawSpread": 0.25,
  "currencyPairIds": [3, 5],
  "requestedBy": "Alice"
}
```
Behavior: `SpreadController.updateGroup` reads the group's current values (`SpreadGroupService.getById`), merges the partial request onto them to build the proposed `after` snapshot (same merge-then-submit pattern as `CurrencyPairController.update`), and calls `auditService.submit("SPREAD_GROUP", UPDATE, id, after, requestedBy)`. `AuditService` calls `handler.snapshotOf(id)` for `before` and checks no `PENDING` request already exists for `(SPREAD_GROUP, id)` (generic `409`). Nothing is persisted to `spread_group`/`spread_group_member` directly.

Validation: same per-field rules as create (uniqueness check excludes the group itself). Semantics for `currencyPairIds` when approved (see `apply(...)`): it **replaces the full membership set** — pairs removed from the list revert to the default spread, pairs added are detached from any other group, pairs unchanged are left alone. If `currencyPairIds` is omitted entirely (`null`/absent) from the request, membership is left unchanged by the eventual `apply(...)`.

Response **`202 Accepted`**: `AuditRequestResponse` with `actionType: "UPDATE"`, `entityId: id`, `status: "PENDING"`, `before: <group's current values>`, `after: <merged proposed values>`.

Response `400`/`404`: same as create. Response `409`: rename collides with a live group's name, **or** a `PENDING` request already exists for this group id (generic dedup, `{"error": "A pending audit request already exists for this entity"}`).

#### 8. Submit Spread Group Delete
```
DELETE /api/spread-groups/{id}
```
Request body optional:
```json
{ "requestedBy": "Alice" }
```
Behavior: `auditService.submit("SPREAD_GROUP", DELETE, id, null, requestedBy)` — `handler.snapshotOf(id)` for `before` (`404` if missing), generic pending-dedup check (`409`). Nothing is deleted from `spread_group`/`spread_group_member`.

Response **`202 Accepted`**: `AuditRequestResponse` with `actionType: "DELETE"`, `entityId: id`, `status: "PENDING"`, `before: <group's current values>`, `after: null`.

Response `404`: not found. Response `409`: a `PENDING` request already exists for this group.

The generic `/api/audit-requests` list/get/approve/reject endpoints used to review and act on all of the above are specified in `specs/backend/audit.md`, not here. Approving a `SPREAD_GROUP`/`DELETE` request removes the group and its memberships; those pairs immediately resolve back to the default spread on their next `GET /api/spread-groups/resolve/{currencyPairId}` call.

### Effective Spread Resolver

#### 9. Resolve Effective Spread for a Currency Pair (unaffected by audit workflow)
```
GET /api/spread-groups/resolve/{currencyPairId}
```
Response `200`:
```json
{
  "currencyPairId": 3,
  "brandId": 1,
  "source": "GROUP",
  "spreadGroupId": 10,
  "spreadGroupName": "Group A",
  "depositSpread": 0.1,
  "withdrawSpread": 0.2
}
```
Always reads live, already-approved `spread_group`/`spread_group_member`/`spread_default` rows — a `PENDING` proposal never affects this endpoint's result. When the pair has no group membership, `source` is `"DEFAULT"`, `spreadGroupId`/`spreadGroupName` are `null`, and `depositSpread`/`withdrawSpread` come from the pair's brand's `spread_default` row.

Response `404`: `currencyPairId` doesn't exist — `{"error": "Currency pair not found", "id": 999}` (reuse existing `CurrencyPairNotFoundException`).

## Implementation Details

### Layer Structure
Same layering as `currency-pair`'s audit integration (`specs/backend/currency-pair-approval.md`): `SpreadController` → `AuditService.submit(...)` for all mutations, plus `SpreadDefaultService` / `SpreadGroupService` (kept for read paths and reused by the handlers' `apply(...)`) → MyBatis mappers (interface + XML) → `SpreadDefault` / `SpreadGroup` / `SpreadGroupMember` model classes, 1:1 with the tables in `specs/dba/spread-default.md`, `specs/dba/spread-group.md`, `specs/dba/spread-group-member.md`. `SpreadDefaultAuditHandler`/`SpreadGroupAuditHandler` live in the `service` package (mirroring `CurrencyPairAuditHandler`), depending on `pl.piomin.services.backend.audit`, never the other way around. Package structure: `pl.piomin.services.backend.{controller,service,mapper,model,dto,exception}`, consistent with `currency-pair`/`brand`.

### Entities
- `SpreadDefault`: `id`, `brandId`, `brandCode` (joined, read-only), `depositSpread`, `withdrawSpread`, `createdAt`, `updatedAt`.
- `SpreadGroup`: `id`, `brandId`, `brandCode` (joined, read-only), `name`, `depositSpread`, `withdrawSpread`, `createdAt`, `updatedAt`.
- `SpreadGroupMember`: `id`, `spreadGroupId`, `currencyPairId`, `baseCurrencyCode`/`quoteCurrencyCode` (joined, read-only), `createdAt`.

### DTOs
- `SpreadDefaultResponse`; `SpreadDefaultUpdateRequest` (`depositSpread`, `withdrawSpread`, both `@NotNull @DecimalMin("0")`, plus optional `requestedBy`).
- `SpreadGroupResponse` (embeds `List<SpreadGroupMemberResponse>`), `SpreadGroupMemberResponse` (`currencyPairId`, `baseCurrencyCode`, `quoteCurrencyCode`).
- `SpreadGroupCreateRequest` (`brandId` `@NotNull`, `name` `@NotBlank`, `depositSpread`/`withdrawSpread` `@NotNull @DecimalMin("0")`, `currencyPairIds` `List<Long>` nullable, optional `requestedBy`).
- `SpreadGroupUpdateRequest` (all fields nullable/optional: `name`, `depositSpread`, `withdrawSpread`, `currencyPairIds`, `requestedBy`).
- `SpreadGroupDeleteRequest` (optional `requestedBy` only — mirrors `CurrencyPairDeleteRequest`).
- `SpreadResolutionResponse` (`currencyPairId`, `brandId`, `source` (`"DEFAULT"`|`"GROUP"`), `spreadGroupId`, `spreadGroupName`, `depositSpread`, `withdrawSpread`).

### Extracted validators (reused by both the `*Service` classes and the audit handlers)
- `SpreadGroupValidator` (new `@Component`, mirroring `CurrencyPairValidator`, `specs/backend/currency-pair-approval.md`): brand-existence, name-non-blank/uniqueness-within-brand (excluding a given id for updates), non-negative spread values, `currencyPairIds` no-duplicates + existence + brand-match.

### Service logic (retained; now called only from the audit handlers' `apply(...)`, never directly from `SpreadController`)

`SpreadDefaultService`:
- `list(Long brandId)`: read all, optionally filtered.
- `getById(Long id)`: `404` via `SpreadDefaultNotFoundException` if missing.
- `update(Long id, BigDecimal depositSpread, BigDecimal withdrawSpread)`: load by id (`404` if missing), set both spread fields, persist, return. Called only by `SpreadDefaultAuditHandler.apply(...)`.

`SpreadGroupService` (all mutating methods `@Transactional`):
- `list(Long brandId)` / `getById(Long id)`: read groups (optionally filtered), each enriched with its members (join `spread_group_member` → `currency_pair` → `currency` for base/quote codes). Unchanged, called directly by `SpreadController` for the `GET` endpoints.
- `create(...)`, `update(...)`, `delete(...)`: same logic as an unaudited version of this feature would have (insert/replace-membership/cascade-delete-membership, exactly as previously drafted), but now called only by `SpreadGroupAuditHandler.apply(...)`, never directly from `SpreadController`.
- `resolveEffectiveSpread(Long currencyPairId)`: unchanged, called directly by `SpreadController` for the resolver endpoint (always live data).

### `SpreadDefaultAuditHandler` (implements `AuditHandler`, `entityType() = "SPREAD_DEFAULT"`)
- `snapshotOf(id)`: load via `SpreadDefaultService.getById` (`404` via `SpreadDefaultNotFoundException`), build the shape shown above.
- `validate(actionType, entityId, after)`: only ever invoked with `UPDATE` (the controller never submits `CREATE`/`DELETE` for this entity type); delegate `depositSpread`/`withdrawSpread` `>= 0` checks to `SpreadGroupValidator`-style shared validation (or a small dedicated `SpreadDefaultValidator` if simpler — implementer's choice, following whichever extraction pattern keeps `SpreadDefaultService` and this handler from duplicating the check).
- `apply(actionType, entityId, after)`: calls `SpreadDefaultService.update(entityId, after.depositSpread, after.withdrawSpread)`. Returns `entityId` unchanged (this entity type never has a `CREATE`/`DELETE` path).
- `summarize(snapshot)`: `"{brandCode} · 預設點差"`.

### `SpreadGroupAuditHandler` (implements `AuditHandler`, `entityType() = "SPREAD_GROUP"`)
- `snapshotOf(id)`: load the enriched group via `SpreadGroupService.getById` (`404` via `SpreadGroupNotFoundException`), build the shape shown above (`currencyPairIds` derived from `members`).
- `validate(actionType, entityId, after)`: delegate to `SpreadGroupValidator` for brand-existence, name-uniqueness-within-brand (excluding `entityId` for `UPDATE`), non-negative spreads, and `currencyPairIds` no-duplicates/existence/brand-match; enrich `after` in place with `brandCode`/`members` (matching `CurrencyPairAuditHandler`'s enrichment pattern). For `CREATE` only, and only on the *original* submission (not on re-validation at approval time — detect this the same way `CurrencyPairAuditHandler` does, e.g. by checking whether `after` is already enriched with `brandCode`, since `validate` itself is what adds it and always runs once before the row is persisted — see `specs/backend/currency-pair-approval.md`'s "Notable judgment call" on the self-collision bug this avoids), check no `PENDING` `SPREAD_GROUP`/`CREATE` request already exists for the same `(brandId, name)` via `AuditRequestMapper`, throwing `DuplicatePendingSpreadGroupCreateException` (`409`) if so.
- `apply(actionType, entityId, after)`:
  - `CREATE`: insert the `spread_group` row via `SpreadGroupService`'s create path, then for each `currencyPairId` in `after.currencyPairIds`: delete any existing `spread_group_member` row for that pair (detaching a prior group, if any), then insert a new membership row pointing at the newly created group. Returns the new group's id.
  - `UPDATE`: persist `name`/`depositSpread`/`withdrawSpread` onto the existing row; if `after.currencyPairIds` is present, replace the full membership set (remove absent pairs, add-and-detach-elsewhere new pairs, leave unchanged pairs alone — same three-way diff as the originally-drafted `SpreadGroupService.update`). Returns `entityId`.
  - `DELETE`: delete the group's `spread_group_member` rows, then the `spread_group` row itself. Returns `entityId`.
- `summarize(snapshot)`: `"{brandCode} · {name}"`.

### Error Handling
Add to `GlobalExceptionHandler`, following the existing `XxxNotFoundException` → `404` and `DuplicatePendingCurrencyPairCreateException`-style `409` handler patterns:
- `SpreadDefaultNotFoundException` → `404` `{"error": "Spread default not found", "id": ...}`
- `SpreadGroupNotFoundException` → `404` `{"error": "Spread group not found", "id": ...}`
- `SpreadGroupNameExistsException` → `409` `{"error": "Spread group name already exists for this brand", "brandId": ..., "name": ...}` (live-duplicate case)
- `DuplicatePendingSpreadGroupCreateException` → `409` `{"error": "A pending create request already exists for this brand/name combination"}` (pending-duplicate case for `CREATE`, this handler's own responsibility per `specs/backend/audit.md`)
- `InvalidSpreadGroupMemberException` → `400` `{"error": "Currency pair does not belong to the group's brand", "currencyPairId": ..., "brandId": ...}`
- `DuplicateSpreadGroupMemberException` → `400` `{"error": "Duplicate currency pair id in currencyPairIds", "currencyPairId": ...}`
- Reuses existing `BrandNotFoundException`, `CurrencyPairNotFoundException`, and the generic `DuplicatePendingAuditRequestException`/`AuditRequestAlreadyReviewedException` handlers as-is.

### Out of scope (explicitly)
- No entity-specific special-casing inside `AuditController`/`AuditService`/`AuditHandler` — the generic module (`specs/backend/audit.md`) is not modified.
- No defense against two different `PENDING` `SPREAD_GROUP` requests (e.g. one `UPDATE` on Group A adding pair X, one `UPDATE` on Group B also adding pair X) both being approved — the second `apply(...)` to run simply wins (last-approved membership sticks), matching the same last-writer-wins behavior already accepted for currency-pair's own audit workflow. Reviewers are expected to notice overlapping proposals from the `before`/`after` diff before approving both.

## Acceptance Criteria
- [x] `GET /api/spread-defaults` and `GET /api/spread-defaults?brandId=` return the correct rows, unaffected by any pending requests
- [x] `GET /api/spread-defaults/{id}` returns 200 or 404
- [x] `PUT /api/spread-defaults/{id}` creates a `PENDING` `SPREAD_DEFAULT`/`UPDATE` audit request and returns `202`; the live row is unchanged until approved; a second submission while one is `PENDING` returns `409`
- [x] Approving a `SPREAD_DEFAULT`/`UPDATE` request updates the live `spread_default` row with the `after` snapshot's values
- [x] No `POST`/`DELETE` endpoint exists for `/api/spread-defaults`
- [x] `POST /api/spread-groups` creates a `PENDING` `SPREAD_GROUP`/`CREATE` audit request and returns `202`; rejects duplicate name against a **live** group (409), a duplicate name against another **pending** create in the same brand (409), unknown/mismatched-brand pair ids (404/400), and duplicate ids in the payload (400); nothing is inserted into `spread_group`/`spread_group_member` until approved
- [x] Approving a `SPREAD_GROUP`/`CREATE` request inserts the group and its memberships and sets the request's `entityId`
- [x] `PUT /api/spread-groups/{id}` creates a `PENDING` `SPREAD_GROUP`/`UPDATE` audit request with `before`/`after` reflecting the merged proposed state and returns `202`; the live group is unchanged until approved
- [x] Approving a `SPREAD_GROUP`/`UPDATE` request whose `after.currencyPairIds` differs from the live membership set fully replaces membership — removed pairs revert to the default spread, added pairs are detached from any prior group
- [x] `DELETE /api/spread-groups/{id}` creates a `PENDING` `SPREAD_GROUP`/`DELETE` audit request and returns `202`; the live group and its memberships are unchanged until approved
- [x] Approving a `SPREAD_GROUP`/`DELETE` request removes the group and its memberships; previously-member pairs resolve back to the default spread on the next resolve call
- [x] Submitting a second create/update/delete for the same spread default row, or the same spread group (or same brand/name combination for create), while one is still `PENDING` returns `409`
- [x] Approving a `SPREAD_GROUP`/`SPREAD_DEFAULT` request whose re-validation now fails (e.g. brand disabled, duplicate name now exists) returns the appropriate `400`/`404`/`409` and leaves the request `PENDING`
- [x] `GET /api/spread-groups/resolve/{currencyPairId}` always reflects live, approved data — a `PENDING` proposal never changes its result
- [x] `GET /api/spread-defaults`, `GET /api/spread-defaults/{id}`, `GET /api/spread-groups`, `GET /api/spread-groups/{id}` behavior is unchanged from a pre-audit design (still reads live data directly)
- [x] Unit tests for `SpreadDefaultAuditHandler` and `SpreadGroupAuditHandler` (validate/apply/snapshotOf/summarize, all branches) and for `SpreadDefaultService`/`SpreadGroupService`
- [x] Integration tests for `SpreadController` endpoints, including full submit → approve/reject round trips via `/api/audit-requests/{id}/approve|reject`

---
## Execution Result
- Status: DONE
- Files changed:
  - **Models** (new): `develop/backend/src/main/java/pl/piomin/services/backend/model/SpreadDefault.java`, `SpreadGroup.java`, `SpreadGroupMember.java` — 1:1 with `spread_default`/`spread_group`/`spread_group_member` (`specs/dba/spread-default.md`, `specs/dba/spread-group.md`, `specs/dba/spread-group-member.md`), with joined read-only `brandCode`/`baseCurrencyCode`/`quoteCurrencyCode` fields matching the `CurrencyPair`/`Brand` convention.
  - **Mappers** (new): `SpreadDefaultMapper`/`.xml`, `SpreadGroupMapper`/`.xml`, `SpreadGroupMemberMapper`/`.xml` under `mapper`/`resources/mapper` — enriched joins to `brand`/`currency_pair`/`currency` for `brandCode`/`baseCurrencyCode`/`quoteCurrencyCode`, plus test-only `insert`/`deleteById`/`findAllIds` helpers on `SpreadDefaultMapper` mirroring `BrandMapper`'s "seed/clean the fixed set" convention (production never creates/deletes a `spread_default` row through the API).
  - **DTOs** (new): `SpreadDefaultResponse`, `SpreadDefaultUpdateRequest`, `SpreadGroupResponse`, `SpreadGroupMemberResponse`, `SpreadGroupCreateRequest`, `SpreadGroupUpdateRequest`, `SpreadGroupDeleteRequest`, `SpreadResolutionResponse` under `dto`.
  - **Exceptions** (new) under `exception`: `SpreadDefaultNotFoundException`, `SpreadGroupNotFoundException`, `SpreadGroupNameExistsException`, `DuplicatePendingSpreadGroupCreateException`, `InvalidSpreadGroupMemberException`, `DuplicateSpreadGroupMemberException`, and `InvalidSpreadException` (a small addition beyond the spec's explicit list — a shared 400 for spread-field validation failures re-checked from an audit snapshot that bypasses DTO bean validation: negative deposit/withdraw spread on either entity, and blank/over-100-char group names, mirroring `InvalidCurrencyPairException`'s role for `CURRENCY_PAIR`).
  - `develop/backend/src/main/java/pl/piomin/services/backend/exception/GlobalExceptionHandler.java` (edited) — added handlers for all seven exceptions above (404/409/409/400/400/400 respectively, per the spec's table, plus `InvalidSpreadException` → 400).
  - **Services** (new) under `service`: `SpreadGroupValidator` (shared brand-existence, name non-blank/uniqueness-within-brand, non-negative-spread, and currencyPairIds no-duplicates/existence/brand-match checks, reused by both handlers below, mirroring `CurrencyPairValidator`); `SpreadDefaultService` (list/getById/update, `update` called only from the handler's `apply`); `SpreadGroupService` (list/getById/getMembers called directly by `SpreadController` for `GET`; `create`/`update`/`delete` called only from the handler's `apply`, implementing the three-way membership diff — remove-absent/add-and-detach-elsewhere/leave-unchanged — for `update`, and detach-then-insert for `create`; `resolveEffectiveSpread` called directly by `SpreadController` for the resolver endpoint, always reading live `spread_group_member`/`spread_group`/`spread_default` rows); `SpreadDefaultAuditHandler` (`entityType="SPREAD_DEFAULT"`); `SpreadGroupAuditHandler` (`entityType="SPREAD_GROUP"`).
  - `develop/backend/src/main/java/pl/piomin/services/backend/controller/SpreadController.java` (new) — hosts both `/api/spread-defaults` and `/api/spread-groups` (plus the `/api/spread-groups/resolve/{currencyPairId}` resolver) in one controller per the spec's "New controller: SpreadController"; `GET`s call the services directly; `PUT`/`POST`/`PUT`/`DELETE` all build an `after` map (merging the partial update onto the current row/group for `PUT`, same pattern as `CurrencyPairController.update`) and call `AuditService.submit(...)`, returning `202` with `AuditRequestResponse`.
  - `develop/backend/src/test/resources/schema.sql` (edited) — added H2 DDL for `spread_default`/`spread_group`/`spread_group_member` (no FK constraints, matching this test schema's existing convention for `currency_pair`/`audit_request`), with the same `UNIQUE(brand_id)` / `UNIQUE(brand_id, name)` / `UNIQUE(currency_pair_id)` constraints as the real migration.
  - `develop/backend/pom.xml`, `develop/backend/README.md` (edited) — version bumped `0.0.5` → `0.0.6`; README documents the two new base paths and the `0.0.6` history entry.
  - **Tests** (new): `SpreadDefaultServiceTest` (6), `SpreadGroupServiceTest` (14), `SpreadDefaultAuditHandlerTest` (10), `SpreadGroupAuditHandlerTest` (22) — all Mockito unit tests; `SpreadControllerTest` (39) — MockMvc integration tests against the H2 schema above, covering every `GET`/`PUT`/`POST`/`DELETE` endpoint's success/400/404/409 branches and full submit→approve round trips (including membership move-on-create, membership replace-on-update with fallback-to-default verified via the resolver, and cascade delete-with-fallback-to-default).
  - Did **not** touch: any DBA migration file (`V006`–`V008`, already applied per the task's pre-flight note), `Brand`/`BrandController`/`BrandService` (per the spec's explicit "not touched" requirement), or the generic `audit` package (`AuditHandler`/`AuditService`/`AuditController`/`AuditRequestMapper`) — confirmed no edits were made there, only new implementations of the existing `AuditHandler` interface.
- Verification performed:
  - `mvn -f develop/backend/pom.xml compile` — `BUILD SUCCESS`.
  - `mvn -f develop/backend/pom.xml clean test` — `BUILD SUCCESS`, `Tests run: 243, Failures: 0, Errors: 0, Skipped: 0` (152 pre-existing tests untouched/unaffected, plus 52 new unit tests + 39 new integration tests for this feature).
  - `mvn -f develop/backend/pom.xml test -Dsurefire.runOrder=random` — `BUILD SUCCESS`, same `243` tests, `0` failures, confirming no test-order/isolation dependency was introduced.
  - `mvn -f develop/backend/pom.xml -DskipTests package` — `BUILD SUCCESS`, jar repackaged.
  - Connected to the live MySQL `wdd` database (`env.md`) and confirmed `spread_default`/`spread_group`/`spread_group_member` already exist with 7 seeded `spread_default` rows (one per existing brand), matching the DBA spec's migration output and this implementation's mapper SQL column names/types.
  - Manually traced every acceptance-criteria scenario against the actual `SpreadController`/`AuditService`/`SpreadDefaultAuditHandler`/`SpreadGroupAuditHandler` code paths while writing this summary, confirming no gaps between the spec's API contract and the implementation.
- Notable judgment calls:
  - **`currencyPairIds` "omitted means unchanged" is implemented by freezing the group's current live membership into the persisted `after` snapshot at submission time**, rather than carrying a null/absent sentinel all the way through to `apply(...)`. `SpreadGroupAuditHandler.validate` fills in `currencyPairIds` (and the `members` enrichment) from `SpreadGroupMemberMapper.findByGroupId(entityId)` whenever the incoming map doesn't already contain the key — which, by construction, only happens on a `PUT` where the request omitted the field (`SpreadController.updateGroup` only puts the key when the caller supplied a replacement list). This keeps `apply(UPDATE)`'s membership-diff logic unconditional (no null-check branch needed) while still honoring the spec's "leave unchanged" semantics, and keeps the persisted `after_snapshot` self-contained for the audit review UI. A side effect, called out explicitly in the spec's own "Out of scope" section as accepted last-writer-wins behavior: if membership changes again (via a different approved request) between this request's submission and its own approval, the frozen list is what gets (re-)applied, not whatever the live membership had drifted to — consistent with how the rest of this snapshot is treated as fixed once submitted.
  - **Self-collision fix for the CREATE natural-key dedup check**, identical in nature to the one documented in `specs/backend/currency-pair-approval.md`: `SpreadGroupAuditHandler.validate` only runs the `(brandId, name)` pending-duplicate check when `!afterSnapshot.containsKey("brandCode")` (i.e. on the original submission, before `validate` itself enriches the map), preventing `AuditService.approve()`'s re-validation pass from finding the request's own already-`PENDING` row and spuriously rejecting it with `409`. Verified via `SpreadGroupAuditHandlerTest.validate_create_skipsPendingDuplicateCheck_whenSnapshotAlreadyEnriched_asAtApprovalTime` and the full create→approve round trip in `SpreadControllerTest`.
  - **`InvalidSpreadException` added beyond the spec's explicit exception list** for two validation rules that can only be meaningfully re-checked from a deserialized `Map<String,Object>` audit snapshot (bypassing Bean Validation entirely) rather than from a DTO: non-negative deposit/withdraw spread (shared by both `SPREAD_DEFAULT` and `SPREAD_GROUP` via `SpreadGroupValidator.validateSpreadNonNegative`, exactly as the spec's Implementation Details section suggests reusing), and blank/over-100-char spread group names on `PUT` (where the DTO field is optional and so cannot carry `@NotBlank`). This mirrors the codebase's existing pattern of a small dedicated 400 exception per feature (`InvalidCurrencyPairException`) rather than overloading one of the spec's more specific exceptions for an unrelated field.
  - **Acceptance criterion "Approving a `SPREAD_GROUP`/`SPREAD_DEFAULT` request whose re-validation now fails ... leaves the request PENDING"** is fully implemented and tested for `SPREAD_GROUP` (`SpreadControllerTest.approve_updateGroupRequest_returns409_andLeavesPending_whenNameCollidesAtApprovalTime`, mirroring the equivalent `CURRENCY_PAIR` test). For `SPREAD_DEFAULT` there is no test of this exact scenario: the only thing `SpreadDefaultAuditHandler.validate` re-checks is `depositSpread`/`withdrawSpread >= 0`, both of which are fixed, immutable numbers captured in the `after` snapshot at submission time and therefore cannot start failing that check between submission and approval (unlike `SPREAD_GROUP`'s name-uniqueness or brand/member checks, which depend on other rows that can change concurrently). The code path exists and is exercised by unit tests (`SpreadDefaultAuditHandlerTest.validate_throws400_when...Negative`) proving `validate` does throw when re-invoked with an invalid snapshot — there is simply no realistic way to *cause* that state to arise only at approval time for this entity, so the criterion is satisfied by design rather than by a dedicated approval-time integration test.

### Teardown — 2026-08-03
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.

### Increment 1 — 2026-08-04
- Status: DONE
- Rebuilt from scratch on the base package `com.wdd.backend` (the prior Execution Result above, from a different snapshot's `pl.piomin.services.backend` layout, no longer applies — this increment supersedes it in place).
- Files changed:
  - **Models** (new) under `develop/backend/src/main/java/com/wdd/backend/model/`: `SpreadDefault.java`, `SpreadGroup.java`, `SpreadGroupMember.java` — 1:1 with `spread_default`/`spread_group`/`spread_group_member` (`specs/dba/spread-default.md`, `specs/dba/spread-group.md`, `specs/dba/spread-group-member.md`), each with a joined, read-only enrichment field (`brandCode`; `baseCurrencyCode`/`quoteCurrencyCode` for members) matching `CurrencyPair`'s convention.
  - **Mappers** (new) under `mapper`/`resources/mapper`: `SpreadDefaultMapper`/`.xml` (`findAll`/`findById`/`findByBrandId`/`update` — no `insert`/`deleteById`, since a `spread_default` row is seeded 1:1 per brand and never created/removed through the API); `SpreadGroupMapper`/`.xml` (`findAll`/`findById`/`findByBrandAndName`/`insert`/`update`/`deleteById`); `SpreadGroupMemberMapper`/`.xml` (`findByGroupId`/`findByCurrencyPairId`/`insert`/`deleteByCurrencyPairId`/`deleteByGroupId`) — all enriched via joins to `brand`/`currency_pair`/`currency` for `brandCode`/`baseCurrencyCode`/`quoteCurrencyCode`, mirroring `CurrencyPairMapper.xml`'s `enrichedSelect` pattern.
  - **DTOs** (new) under `dto`: `SpreadDefaultResponse`, `SpreadDefaultUpdateRequest`, `SpreadGroupMemberResponse`, `SpreadGroupResponse`, `SpreadGroupCreateRequest`, `SpreadGroupUpdateRequest`, `SpreadGroupDeleteRequest`, `SpreadResolutionResponse`.
  - **Exceptions** (new) under `exception`: `SpreadDefaultNotFoundException`, `SpreadGroupNotFoundException`, `SpreadGroupNameExistsException`, `DuplicatePendingSpreadGroupCreateException`, `InvalidSpreadGroupMemberException`, `DuplicateSpreadGroupMemberException` — the six the spec lists — plus one addition beyond the spec's explicit list: `InvalidSpreadException`, a small dedicated 400 (mirroring `InvalidCurrencyPairException`'s role for `CURRENCY_PAIR`) for two rules that must be re-checked from a deserialized `Map<String,Object>` audit snapshot rather than a DTO: non-negative deposit/withdraw spread (shared by `SPREAD_DEFAULT` and `SPREAD_GROUP` via `SpreadGroupValidator.requireSpreadNonNegative`, exactly as the spec's Implementation Details section suggests) and blank/over-100-char group names on a partial `PUT` update (where the DTO field is nullable and so cannot carry `@NotBlank`).
  - `develop/backend/src/main/java/com/wdd/backend/exception/GlobalExceptionHandler.java` (edited) — added handlers for all seven exceptions above (404/404/409/409/400/400/400 respectively).
  - **Services** (new) under `service`: `SpreadGroupValidator` (shared brand-existence, name-validity/uniqueness-within-brand, non-negative-spread, and currencyPairIds no-duplicates/existence/brand-match checks, reused by both handlers, mirroring `CurrencyPairValidator`); `SpreadDefaultService` (`list`/`getById` called directly by `SpreadController`; `update` called only from `SpreadDefaultAuditHandler.apply`); `SpreadGroupService` (`list`/`getById`/`resolveEffectiveSpread` called directly by `SpreadController`; `create`/`update`/`delete` called only from `SpreadGroupAuditHandler.apply`, implementing the three-way membership diff for `update` — remove-absent/add-and-detach-elsewhere/leave-unchanged — and detach-then-insert for `create`); `SpreadDefaultAuditHandler` (`entityType="SPREAD_DEFAULT"`, UPDATE only, `CREATE`/`DELETE` throw `UnsupportedOperationException`); `SpreadGroupAuditHandler` (`entityType="SPREAD_GROUP"`, CREATE/UPDATE/DELETE).
  - `develop/backend/src/main/java/com/wdd/backend/controller/SpreadController.java` (new) — hosts `/api/spread-defaults` (`GET` list/by-id direct; `PUT` submits `SPREAD_DEFAULT`/`UPDATE`; no `POST`/`DELETE`) and `/api/spread-groups` (`GET` list/by-id direct; `POST`/`PUT`/`DELETE` submit `SPREAD_GROUP` `CREATE`/`UPDATE`/`DELETE`) plus `GET /api/spread-groups/resolve/{currencyPairId}` (always live).
  - `develop/backend/src/test/resources/schema.sql` (edited) — added H2 DDL for `spread_default`/`spread_group`/`spread_group_member`, no FK constraints (matching this test schema's existing convention for `currency_pair`/`audit_request` — each controller test class independently wipes/reseeds its own tables), with the same `UNIQUE(brand_id)`/`UNIQUE(brand_id, name)`/`UNIQUE(currency_pair_id)` constraints as the real migration.
  - **Tests** (new): `SpreadDefaultServiceTest` (6), `SpreadGroupServiceTest` (13), `SpreadDefaultAuditHandlerTest` (12), `SpreadGroupAuditHandlerTest` (21) — Mockito unit tests covering every branch of each service/handler method; `SpreadControllerTest` (44) — MockMvc integration tests against the H2 schema above, covering every `GET`/`PUT`/`POST`/`DELETE` endpoint's success/400/404/409 branches and full submit→approve round trips (including membership move-on-create when a pair already belongs to another group, membership replace-on-update with fallback-to-default verified via the resolver, cascade delete-with-fallback-to-default, the CREATE natural-key pending-dedup 409, and the update-time name-collision-at-approval 409-leaves-PENDING scenario).
  - Did **not** touch: any DBA migration (already live in MySQL per the pre-flight check below), `Brand`/`BrandController`/`BrandService`, `CurrencyPairController`/`CurrencyPairService`/`CurrencyPairAuditHandler`, or the generic `audit` package (`AuditHandler`/`AuditService`/`AuditController`/`AuditRequestMapper`) — confirmed no edits were made there, only new implementations of the existing `AuditHandler` interface plus one new controller/service/mapper/DTO/exception set.
- Verification performed:
  - `mvn -f develop/backend/pom.xml compile` — `BUILD SUCCESS` on the first pass (no iteration needed).
  - `mvn -f develop/backend/pom.xml clean test` — `BUILD SUCCESS`, `Tests run: 283, Failures: 0, Errors: 0, Skipped: 0` (187 pre-existing tests across `currency`/`brand`/`currency-pair`/`currency-pair-definition`/`audit` all still pass unmodified, plus 52 new unit tests + 44 new integration tests for this feature — zero regression).
  - `mvn -f develop/backend/pom.xml test -Dsurefire.runOrder=random` — `BUILD SUCCESS`, same `283` tests, `0` failures, confirming no test-order/isolation dependency was introduced.
  - Pre-flight: connected to the live MySQL `wdd` database (`env.md`) via the `mysql` CLI and confirmed `spread_default` (7 rows, one per existing brand), `spread_group` (0 rows), and `spread_group_member` (0 rows) already exist, matching `specs/dba/spread-default.md`/`spread-group.md`/`spread-group-member.md`'s already-applied migrations and this implementation's mapper SQL column names/types.
  - Live smoke test against the running application (`mvn spring-boot:run` on port 8080 from `application.yml`, connected to the real MySQL `wdd` database): `GET /api/spread-defaults` returned all 7 seeded rows with correct `brandCode` enrichment; `GET /api/spread-defaults/999999` returned `404` with the documented error body; `GET /api/spread-groups/resolve/999999` returned `404 Currency pair not found`; `POST /api/spread-groups` (brand `AU`, empty `currencyPairIds`) returned `202 PENDING` with an enriched `after` snapshot and did not insert any row; approving it via `POST /api/audit-requests/{id}/approve` inserted the `spread_group` row and set `entityId`; the group then appeared via `GET /api/spread-groups`; `DELETE`ing and approving it removed the row again. All smoke-test `audit_request` rows were deleted afterward and the server was stopped, leaving the live database exactly as found (`brand`=7, `currency`=10, `currency_pair`=0, `spread_default`=7, `spread_group`=0, `spread_group_member`=0, `audit_request`=0).
  - Manually traced every Acceptance Criteria item against the actual `SpreadController`/`AuditService`/`SpreadDefaultAuditHandler`/`SpreadGroupAuditHandler`/`SpreadGroupService` code paths and the corresponding test(s) exercising it; all items checked off above.
- Notable judgment calls (carried over from the prior snapshot's reasoning, re-verified against this rebuild's actual code):
  - **`currencyPairIds` "omitted means unchanged" is implemented by freezing the group's current live membership into the persisted `after` snapshot at validation time**, not by threading a null/absent sentinel through to `apply(...)`. `SpreadGroupAuditHandler.validate` (via `resolveCurrencyPairIds`) only falls back to `SpreadGroupMemberMapper.findByGroupId(entityId)` when the incoming map doesn't already contain the `currencyPairIds` key — which only happens when `SpreadController.updateGroup` omitted it because the caller's `SpreadGroupUpdateRequest.currencyPairIds` was null. This keeps `apply(UPDATE)`'s membership-diff logic unconditional while honoring "leave unchanged" semantics, at the cost of freezing the membership set at submission time rather than at approval time (an accepted last-writer-wins tradeoff per the spec's own "Out of scope" section). Verified by `SpreadGroupAuditHandlerTest.validate_update_freezesLiveMembership_whenCurrencyPairIdsOmitted` and `SpreadGroupAuditHandlerTest.validate_update_usesProvidedCurrencyPairIds_withoutLookingUpLiveMembership`.
  - **Self-collision avoidance for the CREATE natural-key dedup check**, identical in nature to the technique documented in `specs/backend/currency-pair-approval.md`'s historical CREATE case: `SpreadGroupAuditHandler.validate` only runs the `(brandId, name)` pending-duplicate check (`hasPendingCreateForBrandAndName`, querying `AuditRequestMapper.findAll("SPREAD_GROUP", "PENDING", "CREATE")` and comparing parsed JSON snapshots in Java) when `!afterSnapshot.containsKey("brandCode")` — i.e. on the original submission, before `validate` itself adds that key — preventing `AuditService.approve()`'s re-validation pass from finding the request's own still-`PENDING` row and spuriously rejecting it with `409`. Verified via `SpreadGroupAuditHandlerTest.validate_create_skipsPendingDuplicateCheck_whenSnapshotAlreadyEnriched_asAtApprovalTime` (unit) and `SpreadControllerTest.approve_createGroupRequest_insertsGroupAndMemberships_andSetsEntityId` (integration, which would fail with `409` on approval without this fix).
  - **`InvalidSpreadException` added beyond the spec's explicit six-exception list**, for the reason given above under "Exceptions". This mirrors the prior snapshot's own judgment call and is re-confirmed sound on rebuild: `SpreadGroupValidator.requireSpreadNonNegative`/`requireNameValid` are the sole throw sites, shared by both `SpreadDefaultAuditHandler` and `SpreadGroupAuditHandler`.
  - **The re-validation-fails-at-approval-time acceptance criterion** is fully covered for `SPREAD_GROUP` (`SpreadControllerTest.approve_updateGroupRequest_returns409_andLeavesPending_whenNameCollidesAtApprovalTime` — a second live group acquires the target name between submission and approval) but, as in the prior snapshot's reasoning, has no equivalent integration scenario for `SPREAD_DEFAULT`: its only re-validated rule (`depositSpread`/`withdrawSpread >= 0`) is fixed at submission time and cannot drift before approval, so the criterion is satisfied by design (and by the unit-level `SpreadDefaultAuditHandlerTest.validate_throwsInvalidSpread_when...Negative` tests proving `validate` does throw when given an invalid snapshot) rather than by a dedicated approval-time integration test.
