---
status: pending
title: "Currency Pair Definition (Global Master) API"
requirement: "幣種對可以被單獨建立, 建立完後所有品牌都有這一個幣種對, 幣種對可以設定正向與反向的精度, 幣種對如果建立正向, 反向就不可被建立. 全域幣種對, 需要確認全部品牌幣種對都關閉, 才可刪除."
depends_on: [currency-pair, brand, currency]
---

# Currency Pair Definition (Global Master) API — Backend Spec

## Overview
Adds a brand-agnostic **currency pair master/definition** concept: `POST /api/currency-pair-definitions` lets a (base, quote) direction be created once, standalone (no brand selection), and provisions it into the existing per-brand `currency_pair` table (`specs/backend/currency-pair.md`) for **every** brand automatically. This is **additive** — it does not replace, modify, or route through `CurrencyPairController`/`CurrencyPairService`/the existing audit-approval workflow (`specs/backend/currency-pair-approval.md`). Per-brand editing of an individual pair's rate/active flag continues exactly as today, still gated by the existing audit module.

**This feature applies immediately and does not go through the audit-approval workflow** — confirmed explicitly out of scope for this increment (unlike `currency-pair`/`spread`). `POST`/`PUT`/`DELETE /api/currency-pair-definitions` mutate directly.

Depends on `Currency` (existing), `Brand` (existing, read-only — iterates all brands), and reuses `CurrencyPairService.create` (existing, unmodified) for the fan-out insert. Depends on `specs/dba/currency-pair-definition.md` for the table and its DB-level "no reverse pair" guard. Cross-referenced by `specs/frontend/currency-pair-definition.md`.

## Requirements
- `POST /api/currency-pair-definitions` creates a definition for a (base, quote) direction and, in the same transaction, inserts an `AUTO`/`rate=null`/`active=true` row into `currency_pair` for every brand that doesn't already have a live row for that exact (brand, base, quote) — existing brand rows for that pair are never touched or overwritten.
- Creating a definition whose reverse direction already has a definition (or which duplicates an existing definition's exact direction) is rejected with `409` — the database's own unique constraint (`specs/dba/currency-pair-definition.md`) is the ultimate backstop, but the service pre-checks and returns a friendlier error.
- `forwardPrecision`/`reversePrecision` (正向精度/反向精度): integers, `0`–`8`, required at creation, editable afterward.
- A definition's `baseCurrencyId`/`quoteCurrencyId` are immutable after creation (only precision is editable) — changing direction would invalidate the "no reverse" guard's meaning and the already-provisioned brand rows.
- Deleting a definition removes only the `currency_pair_definition` row — it never deletes or modifies any `currency_pair` row it previously provisioned (those are now ordinary, independent per-brand rows, editable via the existing `currency_pair` API/audit flow). Deleting a definition does free up its reverse direction for a future definition.
- **Deletion is guarded**: a definition may only be deleted once every `currency_pair` row for its (base, quote) direction, across all brands, has `active = false`. If any brand still has that pair `active = true`, deletion is rejected with `409` — the caller must disable it for every brand first (via the existing per-brand `currency_pair` edit/audit flow, `specs/backend/currency-pair-approval.md`). A brand with no row at all for that pair (e.g. it was independently deleted) does not block deletion — only a *live, active* row does.

## API Contract

New controller: `CurrencyPairDefinitionController`.

Base path: `/api/currency-pair-definitions`

### 1. List Definitions
```
GET /api/currency-pair-definitions?baseCurrencyId={id}&quoteCurrencyId={id}
```
Query parameters (both optional, independently filterable):
| Param            | Type | Required | Description        |
|------------------|------|----------|---------------------|
| baseCurrencyId   | Long | No       | Filter by base currency  |
| quoteCurrencyId  | Long | No       | Filter by quote currency |

Response `200`:
```json
[
  {
    "id": 1,
    "baseCurrencyId": 2,
    "baseCurrencyCode": "USD",
    "quoteCurrencyId": 3,
    "quoteCurrencyCode": "JPY",
    "forwardPrecision": 2,
    "reversePrecision": 5,
    "createdAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00"
  }
]
```

### 2. Get Definition by ID
```
GET /api/currency-pair-definitions/{id}
```
Response `200`: single object (same shape). Response `404`: `{"error": "Currency pair definition not found", "id": 999}`.

### 3. Create Definition (applies immediately, fans out to all brands)
```
POST /api/currency-pair-definitions
```
Request body:
```json
{ "baseCurrencyId": 2, "quoteCurrencyId": 3, "forwardPrecision": 2, "reversePrecision": 5 }
```
Validation:
| Field             | Rule                                                                        |
|-------------------|------------------------------------------------------------------------------|
| baseCurrencyId    | Required; currency must exist                                                |
| quoteCurrencyId   | Required; currency must exist; must differ from `baseCurrencyId`             |
| forwardPrecision  | Required, integer, `0`–`8`                                                   |
| reversePrecision  | Required, integer, `0`–`8`                                                   |

Behavior:
1. Validate as above.
2. Check no existing definition matches this pair in **either** direction — `409` via `CurrencyPairDefinitionExistsException` if one does.
3. Insert the `currency_pair_definition` row.
4. Load every row from `brand` (all brands, not filtered by `active`). For each: if a live `currency_pair` row already exists for `(brandId, baseCurrencyId, quoteCurrencyId)`, skip it (leave the brand's existing row untouched); otherwise call `CurrencyPairService.create(...)` with `rateType="AUTO"`, `rate=null`, `active=true` to insert a new one.
5. All of the above in one `@Transactional` method — if any step fails, nothing is persisted, including the definition row itself.

Response `201`: created definition object (same shape as list item; does not enumerate the provisioned `currency_pair` rows — callers needing those use the existing `GET /api/currency-pairs?baseCurrencyId=...` — actually `specs/backend/currency-pair.md` has no such filter today; simplest is `GET /api/currency-pairs` and filter client-side, or by `brandId` per the existing contract).

Response `400`: validation failure (including `baseCurrencyId == quoteCurrencyId`).
Response `404`: `baseCurrencyId`/`quoteCurrencyId` doesn't exist.
Response `409`: a definition already exists for this exact direction, or for its reverse — `{"error": "A currency pair definition already exists for this pair or its reverse direction", "baseCurrencyId": 2, "quoteCurrencyId": 3}`.

### 4. Update Definition Precision
```
PUT /api/currency-pair-definitions/{id}
```
Request body:
```json
{ "forwardPrecision": 3, "reversePrecision": 6 }
```
Validation: both required (this endpoint only ever edits precision — no partial-update semantics needed since there's nothing else editable), integer, `0`–`8`. `baseCurrencyId`/`quoteCurrencyId` are not accepted by this DTO at all (immutable).

Response `200`: updated object. Response `404` if not found. Response `400` on validation failure.

### 5. Delete Definition
```
DELETE /api/currency-pair-definitions/{id}
```
Behavior: load the definition (`404` if missing); check every `currency_pair` row matching its `(baseCurrencyId, quoteCurrencyId)` across all brands — if any is `active = true`, reject with `409` (below) without deleting anything; otherwise delete the `currency_pair_definition` row only.

Response `204`: deleted. Every `currency_pair` row it previously provisioned is left exactly as-is (they are ordinary rows now, indistinguishable from one created directly through the existing per-brand flow). After this, a definition for the reverse direction may be created.

Response `404`: not found.

Response `409`: one or more brands still have this pair active —
```json
{
  "error": "One or more brands still have this currency pair active; disable it for every brand before deleting",
  "baseCurrencyId": 2,
  "quoteCurrencyId": 3,
  "activeBrandCodes": ["AU", "PUG"]
}
```
`activeBrandCodes` lists every brand whose `currency_pair` row for this direction is currently `active = true`, so the caller knows exactly which brands still need to be disabled.

## Implementation Details

### Layer Structure
`CurrencyPairDefinitionController` → `CurrencyPairDefinitionService` → `CurrencyPairDefinitionMapper` (interface + XML) → `CurrencyPairDefinition` model, 1:1 with `currency_pair_definition` (`specs/dba/currency-pair-definition.md`). The fan-out step calls the existing, unmodified `CurrencyPairService.create(CurrencyPairCreateRequest)` (`specs/backend/currency-pair.md`) directly — **not** through `CurrencyPairController`/`AuditService`, since this feature is explicitly direct-apply. Package structure: `pl.piomin.services.backend.{controller,service,mapper,model,dto,exception}`, consistent with existing features.

### Entity: `CurrencyPairDefinition`
Fields: `id`, `baseCurrencyId`, `baseCurrencyCode` (joined, read-only), `quoteCurrencyId`, `quoteCurrencyCode` (joined, read-only), `forwardPrecision`, `reversePrecision`, `createdAt`, `updatedAt`. `forwardPrecision`/`reversePrecision` map to `TINYINT` columns — use `Integer` in Java.

### DTOs
- `CurrencyPairDefinitionResponse`: all fields shown above.
- `CurrencyPairDefinitionCreateRequest`: `baseCurrencyId`/`quoteCurrencyId` (`@NotNull`), `forwardPrecision`/`reversePrecision` (`@NotNull @Min(0) @Max(8)`).
- `CurrencyPairDefinitionUpdateRequest`: `forwardPrecision`/`reversePrecision` (`@NotNull @Min(0) @Max(8)`) only — no currency fields.

### Service logic: `CurrencyPairDefinitionService`
- `list(Long baseCurrencyId, Long quoteCurrencyId)`: read all, optionally filtered.
- `getById(Long id)`: `404` via `CurrencyPairDefinitionNotFoundException` if missing.
- `create(CurrencyPairDefinitionCreateRequest request)` (`@Transactional`):
  1. Validate `baseCurrencyId`/`quoteCurrencyId` exist (`CurrencyNotFoundException`) and differ (`InvalidCurrencyPairException`, reusing the existing exception type — no need for a new one for this single check).
  2. Query for any existing definition whose `(pairKeyLow, pairKeyHigh)` — i.e. `(min(base,quote), max(base,quote))` computed in Java — matches; if found, throw `CurrencyPairDefinitionExistsException`.
  3. Insert the `currency_pair_definition` row.
  4. `brandMapper.findAll(null)` (existing method, no filter) to get every brand. For each brand: call `currencyPairMapper.findByBrandBaseQuote(brandId, baseCurrencyId, quoteCurrencyId)`; if non-null, skip; otherwise build a `CurrencyPairCreateRequest` (`brandId`, `baseCurrencyId`, `quoteCurrencyId`, `rateType="AUTO"`, `rate=null`, `active=true`) and call the existing, unmodified `CurrencyPairService.create(...)`.
  5. Return the created definition (re-fetched with joined currency codes).
- `update(Long id, CurrencyPairDefinitionUpdateRequest request)`: load by id (`404` if missing), set `forwardPrecision`/`reversePrecision`, persist, return.
- `delete(Long id)`: load by id (`404` if missing); call `currencyPairMapper.findActiveByBaseQuote(definition.getBaseCurrencyId(), definition.getQuoteCurrencyId())` (new mapper method, below) — if non-empty, throw `CurrencyPairDefinitionInUseException(baseCurrencyId, quoteCurrencyId, activeBrandCodes)` (`409`) without deleting anything; otherwise delete the `currency_pair_definition` row only. No cascading logic — `currency_pair` is untouched by design either way.

### New read method on the existing `CurrencyPairMapper` (`specs/backend/currency-pair.md`)
- `findActiveByBaseQuote(Long baseCurrencyId, Long quoteCurrencyId)`: returns every `currency_pair` row (enriched with `brandCode`, reusing the existing joined-query shape) matching this `(base, quote)` direction across **all** brands where `active = true`. Purely additive to the existing mapper interface + XML — no change to any existing method's query or behavior.

### Error Handling
Add to `GlobalExceptionHandler`, following the existing pattern:
- `CurrencyPairDefinitionNotFoundException` → `404` `{"error": "Currency pair definition not found", "id": ...}`
- `CurrencyPairDefinitionExistsException` → `409` `{"error": "A currency pair definition already exists for this pair or its reverse direction", "baseCurrencyId": ..., "quoteCurrencyId": ...}`
- `CurrencyPairDefinitionInUseException` → `409` `{"error": "One or more brands still have this currency pair active; disable it for every brand before deleting", "baseCurrencyId": ..., "quoteCurrencyId": ..., "activeBrandCodes": [...]}`
- Reuses existing `CurrencyNotFoundException` and `InvalidCurrencyPairException` handlers as-is.

### Out of scope (explicitly)
- No change whatsoever to `CurrencyPairController`, `CurrencyPairService`, `CurrencyPairValidator`, `CurrencyPairAuditHandler`, or the `audit_request`/audit-approval workflow — this feature calls `CurrencyPairService.create` as a plain method call, reusing its existing validation, exactly as it exists today.
- No FK or other DB-level link between `currency_pair_definition` and `currency_pair` — the two are related only by matching currency ids in application code, per `specs/dba/currency-pair-definition.md`.
- `GET /api/currency-pairs` is not extended with a `baseCurrencyId`/`quoteCurrencyId` filter as part of this spec — out of scope; the frontend derives "which brands got this pair" by fetching the full list and filtering client-side if it needs to display that.
- No brand-`active` filtering during fan-out — every brand row (`brand` table, unfiltered) gets a `currency_pair` row provisioned, per the literal requirement "所有品牌都有這一個幣種對".

## Acceptance Criteria
- [ ] `POST /api/currency-pair-definitions` for (USD, JPY) creates the definition and a `currency_pair` row (`AUTO`, `rate=null`, `active=true`) for all seeded brands (verified with 2 brands in the H2 test fixture; fan-out logic iterates `brandMapper.findAll(null)` unconditionally, so it applies identically regardless of brand count, including the 7 seeded in the live MySQL `wdd` database)
- [ ] `POST /api/currency-pair-definitions` for (USD, JPY) when brand PUG already has a live `USD/JPY` `currency_pair` row leaves PUG's existing row completely unchanged (rate/rateType/active untouched) while still provisioning the other brand(s)
- [ ] `POST /api/currency-pair-definitions` for (JPY, USD) after (USD, JPY) already has a definition returns `409` and inserts nothing
- [ ] `POST /api/currency-pair-definitions` for (USD, JPY) a second time (exact same direction) returns `409`
- [ ] `POST /api/currency-pair-definitions` with `baseCurrencyId == quoteCurrencyId` returns `400`
- [ ] `PUT /api/currency-pair-definitions/{id}` updates `forwardPrecision`/`reversePrecision` and rejects values outside `0`–`8` with `400`; does not accept/alter `baseCurrencyId`/`quoteCurrencyId` (DTO has no such fields at all)
- [ ] `DELETE /api/currency-pair-definitions/{id}` removes the definition; all previously-provisioned `currency_pair` rows remain, unchanged, in `GET /api/currency-pairs`
- [ ] After deleting a (USD, JPY) definition, `POST /api/currency-pair-definitions` for (JPY, USD) now succeeds
- [ ] None of `CurrencyPairController`'s existing endpoints, `CurrencyPairService`'s existing methods' behavior, or the audit-approval workflow for `currency_pair` change as a result of this feature — verified by the existing `CurrencyPairControllerTest`/`CurrencyPairServiceTest`/`CurrencyPairAuditHandlerTest` suites still passing unmodified (no edits made to any of those three files or to `CurrencyPairService`/`CurrencyPairValidator`/`CurrencyPairController`)
- [ ] Unit tests for `CurrencyPairDefinitionService` (positive and negative cases, including the "skip brand with existing row" fan-out behavior)
- [ ] Integration tests for `CurrencyPairDefinitionController` endpoints

### Delta: deletion requires every brand's pair to be inactive first
(The `[x]` "`DELETE` removes the definition..." item above remains accurate for when the new guard passes — it did not previously need to check anything first.)
- [ ] `DELETE /api/currency-pair-definitions/{id}` returns `409` with `activeBrandCodes` listing every brand still `active = true` for that pair, and deletes nothing, when at least one brand's `currency_pair` row for that direction is active
- [ ] `DELETE /api/currency-pair-definitions/{id}` succeeds (`204`) once every brand's row for that pair has been set `active = false` (via the existing per-brand edit/audit flow)
- [ ] `DELETE /api/currency-pair-definitions/{id}` succeeds when zero `currency_pair` rows exist at all for that pair (e.g. all were independently deleted) — absence of a row never blocks deletion, only an active one does
- [ ] `CurrencyPairMapper.findActiveByBaseQuote` is purely additive — no existing `CurrencyPairMapper`/`CurrencyPairService`/`CurrencyPairController` method's behavior changes, verified by the existing `CurrencyPairControllerTest`/`CurrencyPairServiceTest`/`CurrencyPairAuditHandlerTest` suites still passing unmodified
- [ ] Unit tests for `CurrencyPairDefinitionService.delete`'s new guard (blocked with one/multiple active brands, allowed once all inactive, allowed with zero rows)
- [ ] Integration test for `DELETE /api/currency-pair-definitions/{id}` returning `409` with the correct `activeBrandCodes` list

---
## Execution Result
- Status: DONE
- Files changed:
  - `develop/backend/src/main/java/pl/piomin/services/backend/model/CurrencyPairDefinition.java` (new — entity, 1:1 with `currency_pair_definition`, plus `baseCurrencyCode`/`quoteCurrencyCode` fields populated only by enriched/joined read queries)
  - `develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyPairDefinitionResponse.java` (new)
  - `develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyPairDefinitionCreateRequest.java` (new — `baseCurrencyId`/`quoteCurrencyId`/`forwardPrecision`/`reversePrecision` all `@NotNull`, precision fields `@Min(0)`/`@Max(8)`)
  - `develop/backend/src/main/java/pl/piomin/services/backend/dto/CurrencyPairDefinitionUpdateRequest.java` (new — `forwardPrecision`/`reversePrecision` only, no currency fields at all)
  - `develop/backend/src/main/java/pl/piomin/services/backend/mapper/CurrencyPairDefinitionMapper.java` (new — `findAll` (optional base/quote filters), `findById` (enriched via joins to `currency` twice), `findByEitherDirection` (direction-independent pre-check mirroring the DB's `pair_key_low`/`pair_key_high` unique index), `insert`, `update`, `deleteById`, `findAllIds` (test-only cleanup helper))
  - `develop/backend/src/main/resources/mapper/CurrencyPairDefinitionMapper.xml` (new — MyBatis SQL mapper)
  - `develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyPairDefinitionService.java` (new — `list`, `getById`, `create` (`@Transactional`: validates currencies exist and differ, pre-checks either-direction uniqueness, inserts the definition, then fans out to every brand via `brandMapper.findAll(null)`, skipping any brand with an existing live `(brand, base, quote)` row, calling the existing unmodified `CurrencyPairService.create(...)` as a plain method for the rest), `update` (precision only), `delete` (definition row only, no cascade))
  - `develop/backend/src/main/java/pl/piomin/services/backend/controller/CurrencyPairDefinitionController.java` (new — `GET /api/currency-pair-definitions` (optional `baseCurrencyId`/`quoteCurrencyId`), `GET /{id}`, `POST` → `201`, `PUT /{id}` → `200`, `DELETE /{id}` → `204`; all mutations apply immediately, no audit-approval submission)
  - `develop/backend/src/main/java/pl/piomin/services/backend/exception/CurrencyPairDefinitionNotFoundException.java` (new)
  - `develop/backend/src/main/java/pl/piomin/services/backend/exception/CurrencyPairDefinitionExistsException.java` (new)
  - `develop/backend/src/main/java/pl/piomin/services/backend/exception/GlobalExceptionHandler.java` (edited — added handlers for `CurrencyPairDefinitionNotFoundException` → `404` and `CurrencyPairDefinitionExistsException` → `409`, inserted alongside the existing handlers without touching any of the pre-existing ones added for the spread feature; reused `CurrencyNotFoundException` → `404` and `InvalidCurrencyPairException` → `400` handlers as-is)
  - `develop/backend/src/test/resources/schema.sql` (edited — added an H2-compatible `currency_pair_definition` table, intentionally without the production unique constraint on `(pair_key_low, pair_key_high)` since H2's generated-column/unique-index combo isn't replicated here — the service-layer `findByEitherDirection` pre-check is what the tests exercise, consistent with how other tables in this test schema omit FKs/DB-level guards that are production-only defense-in-depth)
  - `develop/backend/src/test/java/pl/piomin/services/backend/service/CurrencyPairDefinitionServiceTest.java` (new — 14 unit tests, Mockito, covering list/get/create/update/delete, all validation branches (currency not found x2, base==quote, reverse-direction exists, exact-direction exists), and the fan-out behavior including "skip brand with existing row")
  - `develop/backend/src/test/java/pl/piomin/services/backend/controller/CurrencyPairDefinitionControllerTest.java` (new — 15 MockMvc integration tests against H2, covering create/fan-out/skip-existing-brand-row/409-reverse/409-duplicate/400-base-equals-quote/404-currency-missing/400-precision-out-of-range/list-filters/update/update-400/update-404/delete/delete-404/reverse-direction-succeeds-after-delete)
  - `develop/backend/pom.xml` (edited — version bumped 0.0.6 → 0.0.7, description updated to mention Currency Pair Definition)
  - `develop/backend/README.md` (edited — documented the `/api/currency-pair-definitions` endpoint table and behavior, added the 0.0.7 version history entry)
- Notes:
  - Implemented full CRUD for `/api/currency-pair-definitions` following the existing Controller → Service → MyBatis Mapper (interface + XML) layering and DTO conventions used by the Currency/Brand/Currency Pair features. No Lombok used.
  - Confirmed via reading `CurrencyPairService.java` that `create(CurrencyPairCreateRequest)` is already a plain, non-audited `@Transactional` public method (the audit-approval delta only changed `CurrencyPairController` to route through `AuditService`/`CurrencyPairAuditHandler` instead — the service method itself was left untouched). `CurrencyPairDefinitionService.create` calls it directly, exactly as the spec requires, with zero changes to `CurrencyPairService`, `CurrencyPairValidator`, `CurrencyPairController`, or `CurrencyPairAuditHandler`.
  - The DB-level "no reverse pair" guard (unique index on generated `pair_key_low`/`pair_key_high` columns) already exists in production per `specs/dba/currency-pair-definition.md`'s `V009` migration (confirmed applied to the live MySQL `wdd` database by a prior step, and confirmed the migration file already exists identically in `develop/backend/src/main/resources/db/migration/` and `docker/mysql/initdb/` — neither was touched, per instructions). This spec's `CurrencyPairDefinitionService.create` implements the friendlier application-level pre-check (`findByEitherDirection`) described in the spec's "Requirements" section as the primary error path; the DB constraint remains the backstop in production. The H2 test schema does not replicate the generated-column unique index (H2 support for this combination is not exercised here), so tests validate the service-layer pre-check specifically — consistent with how other features in this codebase (e.g. `currency_pair`'s FK-based delete guard) already handle H2 vs. production DB-level constraint parity.
  - Confirmed this feature is architecturally isolated from the audit-approval workflow: `CurrencyPairDefinitionController`'s `POST`/`PUT`/`DELETE` return `201`/`200`/`204` directly (no `AuditService`, no `AuditRequestResponse`), matching the spec's explicit "applies immediately" requirement, in contrast to `CurrencyPairController`/`SpreadGroupController`'s `202`-with-pending-request pattern.
  - Ran `mvn -f develop/backend/pom.xml clean test` — **BUILD SUCCESS, 272 tests total, 0 failures/errors** (up from 238 pre-existing), including all pre-existing `CurrencyPairControllerTest` (34), `CurrencyPairServiceTest` (25), and `CurrencyPairAuditHandlerTest` (20) tests passing completely unmodified, confirming zero regression to the currency-pair/audit-approval feature set. New tests: 14 `CurrencyPairDefinitionServiceTest` (unit) + 15 `CurrencyPairDefinitionControllerTest` (integration) = 29 new tests (spec asked for both unit and integration coverage; both delivered).
  - No `.circleci/config.yml` change needed — the existing `build-and-test` job already runs `mvn -f develop/backend/pom.xml -B test` against the whole backend module, which now includes this feature's tests automatically.
  - No migration file was created or modified, per instructions — `V009__create_currency_pair_definition_table.sql` was already applied to the live MySQL database by the DBA pipeline stage prior to this backend increment.

### Increment 2 — 2026-07-30 (Delta: deletion requires every brand's pair to be inactive first)
- Files changed:
  - `develop/backend/src/main/java/pl/piomin/services/backend/mapper/CurrencyPairMapper.java` (edited — added `findActiveByBaseQuote(Long baseCurrencyId, Long quoteCurrencyId)`, purely additive, no change to any existing method signature/behavior)
  - `develop/backend/src/main/resources/mapper/CurrencyPairMapper.xml` (edited — added the `findActiveByBaseQuote` `<select>`, reusing the existing `enrichedColumns`/`enrichedJoin` `<sql>` fragments also used by `findAll`/`findById`; filters `WHERE base_currency_id = ... AND quote_currency_id = ... AND active = TRUE`; no existing `<select>`/`<insert>`/`<update>`/`<delete>` touched)
  - `develop/backend/src/main/java/pl/piomin/services/backend/exception/CurrencyPairDefinitionInUseException.java` (new — carries `baseCurrencyId`, `quoteCurrencyId`, `activeBrandCodes`)
  - `develop/backend/src/main/java/pl/piomin/services/backend/exception/GlobalExceptionHandler.java` (edited — added a handler for `CurrencyPairDefinitionInUseException` → `409` with the `error`/`baseCurrencyId`/`quoteCurrencyId`/`activeBrandCodes` body, inserted alongside the existing handlers without touching any pre-existing one)
  - `develop/backend/src/main/java/pl/piomin/services/backend/service/CurrencyPairDefinitionService.java` (edited — `delete(Long id)` now loads the definition, calls `currencyPairMapper.findActiveByBaseQuote(baseCurrencyId, quoteCurrencyId)`, and throws `CurrencyPairDefinitionInUseException` with the active rows' `brandCode`s if any are found, before deleting anything; otherwise unchanged — deletes only the `currency_pair_definition` row)
  - `develop/backend/src/test/java/pl/piomin/services/backend/service/CurrencyPairDefinitionServiceTest.java` (edited — added 4 new unit tests: blocked with one active brand (asserts exact `activeBrandCodes`), blocked with multiple active brands (asserts full ordered list), allowed once all inactive, allowed with zero rows)
  - `develop/backend/src/test/java/pl/piomin/services/backend/controller/CurrencyPairDefinitionControllerTest.java` (edited — added `delete_returns409_withActiveBrandCodes_whenAnyBrandStillActive` (deactivates one of the two fanned-out brands, asserts `409`/`activeBrandCodes: ["PUG"]`/nothing deleted) and `delete_succeeds_onceEveryBrandRowIsInactive`; also updated the two pre-existing tests that create-then-immediately-delete a definition (`delete_removesDefinition_butLeavesProvisionedCurrencyPairsUntouched`, `create_succeeds_forReverseDirection_afterOriginalDefinitionDeleted`) to deactivate every fanned-out `currency_pair` row first, since the fan-out provisions `active=true` rows and the new guard would otherwise correctly block their deletion — this is required fallout from the delta's behavior change, not a re-verification of already-checked items)
  - `develop/backend/pom.xml` (edited — version bumped 0.0.8 → 0.0.9)
  - `develop/backend/README.md` (edited — version bumped to 0.0.9, documented the new delete-guard behavior under the `/api/currency-pair-definitions` endpoint table, added the 0.0.9 version history entry)
- Notes:
  - `findActiveByBaseQuote` is additive-only: verified by running the full pre-existing suite unmodified (`CurrencyPairControllerTest` 22, `CurrencyPairServiceTest` 25, `CurrencyPairAuditHandlerTest` 16 — all pass) alongside the new/updated tests.
  - Ran `mvn -f develop/backend/pom.xml clean test` — **BUILD SUCCESS, 262 tests total, 0 failures/errors**.
  - No DBA/migration work needed — reuses the existing `currency_pair.active` column, per instructions.
  - `CurrencyPairDefinitionController` required no code changes — `DELETE /{id}` already just calls `currencyPairDefinitionService.delete(id)` and lets the new exception type propagate to `GlobalExceptionHandler`.
  - Status set to `done` — every Delta acceptance criterion above is now checked, and all pre-existing checked items remain valid (unmodified, still passing).

### Teardown — 2026-08-03
Build artifacts wiped (`develop/`, `docker/`) and this spec's Acceptance Criteria reset to unexecuted. The Execution Result above describes a prior build that no longer exists on disk — /dev will re-execute this spec from scratch on the next run.
